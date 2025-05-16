package io.axor.raft;

import io.axor.api.ActorAddress;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.raft.logging.RaftLogging;
import io.axor.raft.logging.RaftLoggingFactory;
import io.axor.raft.logging.SnapshotStore;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.ClientMessage;
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
    private final List<PeerInstance> peers;
    private final PeerInstance selfPeer;
    private final RaftState raftState;
    private final RaftLogging raftLogging;
    private final TxnManager txnManager;
    private final SnapshotManager snapshotManager;
    private long nextTxnId = 1;
    private LeaderContext leaderContext;

    public RaftContext(ActorContext<PeerMessage> context,
                       RaftConfig config,
                       List<Peer> peers,
                       Peer selfPeer,
                       RaftLoggingFactory factory) throws RaftException {
        this.context = context;
        this.config = config;
        this.raftLogging = factory.createLogging(selfPeer.address().name());
        this.raftState = new RaftState();
        this.txnManager = new TxnManager(this);
        raftLogging.addListener(txnManager.getListener());
        SnapshotStore snapshotStore = factory.createSnapshotStore(selfPeer.address().name());
        this.snapshotManager = new SnapshotManager(snapshotStore, raftLogging,
                config.snapshotEntryInterval(),
                config.snapshotBytesInterval().toBytes(),
                config.snapshotTimeout(),
                context.dispatcher());
        raftLogging.loadSnapshot(snapshotManager.getLatestSnapshot());

        ActorSystem system = context.system();
        try {
            List<PeerInstance> peerInstances = new ArrayList<>(peers.size());
            PeerInstance selfPeerInstance = null;
            for (Peer peer : peers) {
                boolean isSelf = peer.equals(selfPeer);
                PeerInstance peerInstance = new PeerInstance(peer, isSelf,
                        system.get(peer.address(), PeerMessage.class));
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

    public List<PeerInstance> getPeers() {
        return peers;
    }

    public PeerInstance getSelfPeer() {
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

    public PeerInstance getPeerOfSender() {
        ActorAddress address = context.sender().address();
        for (PeerInstance peer : peers) {
            if (peer.peer().address().equals(address)) {
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
        private final Map<Peer, FollowerState> followerStates;
        private final ScheduledFuture<?> scheduleHeartbeat;

        public LeaderContext(RaftContext raftContext) {
            this.raftContext = raftContext;
            Map<Peer, FollowerState> followerStates = new HashMap<>(raftContext.peers.size());
            for (PeerInstance peer : raftContext.peers) {
                followerStates.put(peer.peer(), new FollowerState());
            }
            this.followerStates = Collections.unmodifiableMap(followerStates);
            Duration interval = raftContext.config.leaderHeartbeatInterval();
            scheduleHeartbeat = raftContext.context.scheduler().scheduleWithFixedDelay(() -> {
                for (PeerInstance peer : raftContext.peers) {
                    if (peer.isSelf()) {
                        continue;
                    }
                    RaftState raftState = raftContext.getRaftState();
                    long currentTerm = raftState.getCurrentTerm();
                    LogId commitedId = raftContext.raftLogging.commitedId();
                    ActorRef<PeerMessage> self = raftContext.context.self();
                    peer.peerRef().tell(PeerMessage.newBuilder()
                            .setLeaderHeartbeat(PeerProto.LeaderHeartbeat.newBuilder()
                                    .setTerm(currentTerm)
                                    .setLeaderCommited(commitedId))
                            .build(), self);
                }
            }, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        }

        public Map<Peer, FollowerState> getFollowerStates() {
            return followerStates;
        }

        public boolean bufferIsEmpty() {
            return bufferedClientReqs.isEmpty();
        }

        public boolean bufferClientCtx(ClientTxnReq req) {
            ActorRef<ClientMessage> sender = raftContext.context.sender(ClientMessage.class);
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

    public record ClientTxn(long seqId, ActorRef<ClientMessage> clientRef) {
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

    public record ClientTxnCtx(long seqId, ActorRef<ClientMessage> clientRef, LogId logId) {
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
}
