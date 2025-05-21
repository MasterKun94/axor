package io.axor.raft;

import io.axor.api.ActorAddress;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventStage;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.raft.logging.RaftLogging;
import io.axor.raft.logging.RaftLoggingFactory;
import io.axor.raft.logging.SnapshotStore;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.MediatorMessage;
import io.axor.raft.proto.PeerProto.ClientTxnReq;
import io.axor.raft.proto.PeerProto.LogAppend;
import io.axor.raft.proto.PeerProto.LogEntry;
import io.axor.raft.proto.PeerProto.LogId;
import io.axor.raft.proto.PeerProto.PeerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RaftContext {
    private static final Logger LOG = LoggerFactory.getLogger(RaftContext.class);

    private final ActorContext<PeerMessage> context;
    private final RaftConfig config;
    private final List<ActorRef<PeerMessage>> peers;
    private final ActorRef<PeerMessage> selfPeer;
    private final RaftState raftState;
    private final RaftLogging raftLogging;
    private final TxnManager txnManager;
    private final RaftListenerAdaptor listenerAdaptor = new RaftListenerAdaptor();
    private long nextTxnId = 1;
    private LeaderContext leaderContext;

    public RaftContext(ActorContext<PeerMessage> context,
                       RaftConfig config,
                       List<ActorAddress> peers,
                       ActorAddress selfPeer,
                       RaftLoggingFactory factory) throws RaftException {
        this.context = context;
        this.config = config;
        this.raftLogging = factory.createLogging(selfPeer.name());
        this.raftState = new RaftState();
        this.txnManager = new TxnManager(this);
        SnapshotStore snapshotStore = factory.createSnapshotStore(selfPeer.name());
        SnapshotManager snapshotManager = new SnapshotManager(snapshotStore, raftLogging,
                config.snapshotEntryInterval(),
                config.snapshotBytesInterval().toBytes(),
                config.snapshotTimeout(),
                context.dispatcher());
        raftLogging.addListener(txnManager.getListener());
        raftLogging.addListener(snapshotManager.getListener());
        raftLogging.addListener(listenerAdaptor.getEntryListener());
        raftLogging.loadSnapshot(snapshotManager.getLatestSnapshot());

        ActorSystem system = context.system();
        try {
            List<ActorRef<PeerMessage>> peerInstances = new ArrayList<>(peers.size());
            ActorRef<PeerMessage> selfPeerInstance = null;
            for (ActorAddress peer : peers) {
                boolean isSelf = peer.equals(selfPeer);
                ActorRef<PeerMessage> peerInstance = system.get(peer, PeerMessage.class);
                peerInstances.add(peerInstance);
                if (isSelf) {
                    selfPeerInstance = peerInstance;
                }
            }
            if (selfPeerInstance == null) {
                throw new IllegalArgumentException("selfPeer not exists in peers");
            }
            this.peers = peerInstances;
            this.selfPeer = selfPeerInstance;
        } catch (ActorNotFoundException | IllegalMsgTypeException e) {
            throw new RuntimeException(e);
        }
        raftState.setCurrentTerm(raftLogging.logEndId().getTerm());
        raftState.setLatestHeartbeatTimestamp(System.currentTimeMillis());
    }

    public ActorContext<PeerMessage> getContext() {
        return context;
    }

    public RaftConfig getConfig() {
        return config;
    }

    public List<ActorRef<PeerMessage>> getPeers() {
        return peers;
    }

    public ActorRef<PeerMessage> getSelfPeer() {
        return selfPeer;
    }

    public RaftState getRaftState() {
        return raftState;
    }

    public RaftLogging getRaftLogging() {
        return raftLogging;
    }

    public TxnManager getTxnManager() {
        return txnManager;
    }

    public LeaderContext getLeaderContext() {
        return leaderContext;
    }

    public LogId getLogEndId() {
        return raftLogging.logEndId();
    }

    public void addListener(RaftListener listener) {
        listenerAdaptor.addListener(listener);
    }

    public ActorRef<PeerMessage> getPeerOfSender() {
        ActorAddress address = context.sender().address();
        for (ActorRef<PeerMessage> peer : peers) {
            if (peer.address().equals(address)) {
                return peer;
            }
        }
        throw new IllegalArgumentException("sender: " + address + " is not a peer");
    }

    public void changeSelfPeerState(PeerState peerState) {
        PeerState prev = raftState.getPeerState();
        if (prev != peerState) {
            LOG.info("Peer state changed from {} to {}", prev, peerState);
            raftState.setPeerState(peerState);
            if (peerState == PeerState.LEADER) {
                leaderContext = new LeaderContext(this);
            } else if (leaderContext != null) {
                if (!leaderContext.bufferedClientReqs.isEmpty()) {
                    throw new IllegalStateException("should never happen");
                }
                leaderContext.close();
                leaderContext = null;
            }
        }
    }

    public long generateTxnId() {
        return nextTxnId++;
    }

    public void close() {
        txnManager.close();
        if (leaderContext != null) {
            leaderContext.close();
        }
    }

    public static class LeaderContext {
        private final RaftContext raftContext;
        private final List<ClientTxnReq> bufferedClientReqs = new ArrayList<>();
        private final Map<ActorAddress, FollowerState> followerStates;
        private final ScheduledFuture<?> scheduleHeartbeat;

        public LeaderContext(RaftContext raftContext) {
            this.raftContext = raftContext;
            Map<ActorAddress, FollowerState> followerStates = new HashMap<>(raftContext.peers.size());
            for (ActorRef<PeerMessage> peer : raftContext.peers) {
                followerStates.put(peer.address(), new FollowerState());
            }
            this.followerStates = Collections.unmodifiableMap(followerStates);
            Duration interval = raftContext.config.leaderHeartbeatInterval();
            scheduleHeartbeat = raftContext.context.scheduler().scheduleWithFixedDelay(() -> {
                for (ActorRef<PeerMessage> peer : raftContext.peers) {
                    if (peer.equals(raftContext.selfPeer)) {
                        continue;
                    }
                    RaftState raftState = raftContext.getRaftState();
                    long currentTerm = raftState.getCurrentTerm();
                    LogId commitedId = raftContext.raftLogging.commitedId();
                    ActorRef<PeerMessage> self = raftContext.context.self();
                    peer.tell(PeerMessage.newBuilder()
                            .setLeaderHeartbeat(PeerProto.LeaderHeartbeat.newBuilder()
                                    .setTerm(currentTerm)
                                    .setLeaderCommited(commitedId))
                            .build(), self);
                }
            }, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        }

        public Map<ActorAddress, FollowerState> getFollowerStates() {
            return followerStates;
        }

        public boolean bufferIsEmpty() {
            return bufferedClientReqs.isEmpty();
        }

        public boolean bufferClientCtx(ClientTxnReq req) {
            ActorRef<MediatorMessage> sender = raftContext.context.sender(MediatorMessage.class);
            TxnManager.Key key = new TxnManager.Key(req.getClientId(), req.getSeqId());
            if (raftContext.txnManager.inTxnOrCommited(key)) {
                raftContext.txnManager.addClient(key, sender);
                return false;
            } else {
                raftContext.txnManager.createTxn(key, sender);
                bufferedClientReqs.add(req);
                return true;
            }
        }

        public PeerProto.LogAppend prepareForTxn(long txnId) {
            if (bufferedClientReqs.isEmpty()) {
                throw new IllegalStateException("no pending client txn");
            }
            RaftState raftState = raftContext.getRaftState();
            long term = raftState.getCurrentTerm();
            int bytesLimit = raftContext.getConfig().logAppendBytesLimit().toInt();
            int entryLimit = raftContext.getConfig().logAppendEntryLimit();
            LogId prevLogId = raftContext.raftLogging.commitedId();
            LogId nextLogId = null;
            if (raftContext.raftLogging.uncommitedId().isEmpty()) {
                nextLogId = LogId.newBuilder()
                        .setTerm(term)
                        .setIndex(prevLogId.getIndex() + 1)
                        .build();
            } else {
                for (LogId logId : raftContext.raftLogging.uncommitedId()) {
                    if (logId.getTerm() == term) {
                        nextLogId = logId;
                        break;
                    }
                    assert logId.getTerm() < term;
                    prevLogId = logId;
                }
                if (nextLogId == null) {
                    nextLogId = LogId.newBuilder()
                            .setTerm(term)
                            .setIndex(prevLogId.getIndex() + 1)
                            .build();
                }
            }
            int bytes = 0;
            int cnt = 0;
            LogAppend.Builder builder = LogAppend.newBuilder()
                    .setTxnId(txnId)
                    .setTerm(term)
                    .setPrevLogId(prevLogId)
                    .setLeaderCommited(raftContext.raftLogging.commitedId());
            while (!bufferedClientReqs.isEmpty() && cnt < entryLimit && bytes < bytesLimit) {
                ClientTxnReq req = bufferedClientReqs.removeFirst();
                TxnManager.Key key = new TxnManager.Key(req.getClientId(), req.getSeqId());
                assert raftContext.txnManager.isWaiting(key);
                builder.addEntries(LogEntry.newBuilder()
                        .setId(nextLogId)
                        .setValue(PeerProto.LogValue.newBuilder()
                                .setData(req.getData())
                                .setClientId(req.getClientId())
                                .setSeqId(req.getSeqId())
                                .setTimestamp(raftContext.txnManager.getCreateTime(key))
                                .setControlFlag(req.getControlFlag()))
                        .build());
                entryLimit++;
                bytesLimit += req.getData().size();
                nextLogId = nextLogId.toBuilder().setIndex(nextLogId.getIndex() + 1).build();
            }
            return builder.build();
        }

        public void close() {
            scheduleHeartbeat.cancel(false);
        }
    }

    public record TxnContext(LogAppend append, List<ClientTxnCtx> clientTxnList) {
    }

    public record ClientTxn(long seqId, ActorRef<MediatorMessage> clientRef) {
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            ClientTxn that = (ClientTxn) o;
            return seqId == that.seqId && Objects.equals(clientRef.address(),
                    that.clientRef.address());
        }

        @Override
        public int hashCode() {
            return Objects.hash(seqId, clientRef.address());
        }
    }

    public record ClientTxnCtx(long seqId, ActorRef<MediatorMessage> clientRef, LogId logId) {
    }

    public static class FollowerState {
        private long latestTxnId;
        private LogId commited;
        private List<LogId> uncommited;

        public long getLatestTxnId() {
            return latestTxnId;
        }

        public void setLatestTxnId(long latestTxnId) {
            this.latestTxnId = latestTxnId;
        }

        public LogId getCommited() {
            return commited;
        }

        public void setCommited(LogId commited) {
            this.commited = commited;
        }

        public List<LogId> getUncommited() {
            return uncommited;
        }

        public void setUncommited(List<LogId> uncommited) {
            this.uncommited = uncommited;
        }

        public LogId getLogEndId() {
            return uncommited == null || uncommited.isEmpty() ? commited : uncommited.getLast();
        }

        @Override
        public String toString() {
            return "FollowerState[" +
                   "latestTxnId=" + latestTxnId +
                   ", commited=" + commited +
                   ", uncommited=" + uncommited +
                   ']';
        }
    }

    private static class RaftListenerAdaptor implements RaftListener {
        private final List<LogEntry> uncommitedEntry = new ArrayList<>();
        private final List<RaftListener> listeners = new ArrayList<>();

        @Override
        public void onStateChange(PeerState from, PeerState to) {
            for (RaftListener listener : listeners) {
                listener.onStateChange(from, to);
            }
        }

        @Override
        public void onTermChange(long from, long to) {
            for (RaftListener listener : listeners) {
                listener.onTermChange(from, to);
            }
        }

        @Override
        public void onLeaderChange(ActorAddress leader) {
            for (RaftListener listener : listeners) {
                listener.onLeaderChange(leader);
            }
        }

        @Override
        public void onBecomeLeader() {
            for (RaftListener listener : listeners) {
                listener.onBecomeLeader();
            }
        }

        @Override
        public void onTxnStarted(LogAppend append) {
            for (RaftListener listener : listeners) {
                listener.onTxnStarted(append);
            }
        }

        @Override
        public void onTxnSuccess() {
            for (RaftListener listener : listeners) {
                listener.onTxnSuccess();
            }
        }

        @Override
        public void onTxnFailure() {
            for (RaftListener listener : listeners) {
                listener.onTxnFailure();
            }
        }

        @Override
        public void onExistLeader() {
            for (RaftListener listener : listeners) {
                listener.onExistLeader();
            }
        }

        @Override
        public void onLogCommited(LogEntry entry) {
            for (RaftListener listener : listeners) {
                listener.onLogCommited(entry);
            }
        }

        @Override
        public void onLoadSnapshot(PeerProto.Snapshot snapshot) {
            for (RaftListener listener : listeners) {
                listener.onLoadSnapshot(snapshot);
            }
        }

        @Override
        public void onInstallSnapshot(PeerProto.InstallSnapshot snapshot) {
            for (RaftListener listener : listeners) {
                listener.onInstallSnapshot(snapshot);
            }
        }

        @Override
        public EventStage<PeerProto.Snapshot> takeSnapshot(PeerProto.Snapshot snapshot,
                                                           EventExecutor executor) {
            EventStage<PeerProto.Snapshot> stage = EventStage.succeed(snapshot, executor);
            for (RaftListener listener : listeners) {
                stage = stage.flatmap(s -> listener.takeSnapshot(s, executor));
            }
            return stage;
        }

        public void addListener(RaftListener listener) {
            listeners.add(listener);
        }

        public RaftLogging.LogEntryListener getEntryListener() {
            return new RaftLogging.LogEntryListener() {
                @Override
                public void appended(LogEntry entry) {
                    uncommitedEntry.add(entry);
                }

                @Override
                public void removed(LogId id) {
                    LogEntry logEntry = uncommitedEntry.removeLast();
                    assert logEntry.getId().equals(id);
                }

                @Override
                public void commited(LogId id) {
                    LogEntry logEntry = uncommitedEntry.removeFirst();
                    assert logEntry.getId().equals(id);
                    onLogCommited(logEntry);
                }

                @Override
                public EventStage<PeerProto.Snapshot> takeSnapshot(PeerProto.Snapshot snapshot,
                                                                   EventExecutor executor) {
                    return RaftListenerAdaptor.this.takeSnapshot(snapshot, executor);
                }

                @Override
                public void installSnapshot(PeerProto.InstallSnapshot snapshot) {
                    RaftListenerAdaptor.this.onInstallSnapshot(snapshot);
                }

                @Override
                public void loadSnapshot(PeerProto.Snapshot snapshot) {
                    RaftListenerAdaptor.this.onLoadSnapshot(snapshot);
                }
            };
        }
    }
}
