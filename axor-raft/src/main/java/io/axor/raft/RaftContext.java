package io.axor.raft;

import io.axor.api.ActorAddress;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.raft.logging.AsyncRaftLogging;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RaftContext {
    private static final Logger LOG = LoggerFactory.getLogger(RaftContext.class);

    private final ActorContext<PeerMessage> context;
    private final RaftConfig config;
    private final List<PeerInstance> peers;
    private final PeerInstance selfPeer;
    private final List<Runnable> runOnPeerStateChange = new ArrayList<>();
    private final RaftState raftState;
    private final AsyncRaftLogging raftLogging;
    private long nextTxnId = 1;
    private LeaderContext leaderContext;

    public RaftContext(ActorContext<PeerMessage> context,
                       RaftConfig config,
                       List<Peer> peers,
                       Peer selfPeer,
                       AsyncRaftLogging raftLogging) {
        this.context = context;
        this.config = config;
        this.raftLogging = raftLogging;
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
        this.raftState = new RaftState();
        raftState.setCommitedId(raftLogging.commitedId());
        raftState.setUncommitedId(raftLogging.uncommitedId());
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

    public AsyncRaftLogging getRaftLogging() {
        return raftLogging;
    }

    public LeaderContext getLeaderContext() {
        return leaderContext;
    }

    public LogId getLogEndId() {
        List<LogId> uncommitedId = raftState.getUncommitedId();
        if (uncommitedId == null || uncommitedId.isEmpty()) {
            return raftState.getCommitedId();
        } else {
            return uncommitedId.getLast();
        }
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
            for (Runnable runnable : runOnPeerStateChange) {
                runnable.run();
            }
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

    public void runOnPeerStateChange(Runnable runnable) {
        runOnPeerStateChange.add(runnable);
    }

    public static class LeaderContext {
        private final RaftContext raftContext;
        private final SequencedMap<ClientTxn, ClientTxnReq> bufferedClientReqs =
                new LinkedHashMap<>();
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
                    LogId commitedId = raftState.getCommitedId();
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

        public void bufferClientCtx(ClientTxnReq req) {
            var key = new ClientTxn(req.getTxnId(),
                    raftContext.getContext().sender(ClientMessage.class));
            bufferedClientReqs.put(key, req);
        }

        public TxnContext prepareForTxn(long txnId) {
            if (bufferedClientReqs.isEmpty()) {
                throw new IllegalStateException("no pending client txn");
            }
            List<ClientTxn> clientTxnList = new ArrayList<>();
            long term = raftContext.getRaftState().getCurrentTerm();
            int bytesLimit = raftContext.getConfig().logAppendBytesLimit().toInt();
            int entryLimit = raftContext.getConfig().logAppendEntryLimit();
            LogId commitedId = raftContext.getRaftState().getCommitedId();
            int bytes = 0;
            int cnt = 0;
            LogAppend.Builder builder = LogAppend.newBuilder()
                    .setTxnId(txnId)
                    .setTerm(term)
                    .setLeaderCommited(commitedId);
            while (!bufferedClientReqs.isEmpty() && cnt < entryLimit && bytes < bytesLimit) {
                var entry = bufferedClientReqs.pollFirstEntry();
                commitedId = commitedId.toBuilder().setIndex(commitedId.getIndex() + 1).build();
                ClientTxn key = entry.getKey();
                clientTxnList.add(key);
                ClientTxnReq value = entry.getValue();
                builder.addEntries(LogEntry.newBuilder()
                        .setId(commitedId)
                        .setValue(PeerProto.LogValue.newBuilder()
                                .setData(value.getData())
                                .setClientId(value.getClientId())
                                .setClientTxnId(value.getTxnId()))
                        .build());
                ;
                entryLimit++;
                bytesLimit += value.getData().size();
            }
            return new TxnContext(builder.build(), clientTxnList);
        }

        public void close() {
            scheduleHeartbeat.cancel(false);
        }
    }

    public record TxnContext(LogAppend append, List<ClientTxn> clientTxnList) {
    }

    public record ClientTxn(long txnId, ActorRef<ClientMessage> clientRef) {
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            ClientTxn that = (ClientTxn) o;
            return txnId == that.txnId && Objects.equals(clientRef.address(),
                    that.clientRef.address());
        }

        @Override
        public int hashCode() {
            return Objects.hash(txnId, clientRef.address());
        }
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
            return "FollowerState{" +
                   "latestTxnId=" + latestTxnId +
                   ", commited=" + commited +
                   ", uncommited=" + uncommited +
                   '}';
        }
    }
}
