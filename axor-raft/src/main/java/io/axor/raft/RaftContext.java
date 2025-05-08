package io.axor.raft;

import io.axor.api.ActorAddress;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.raft.logging.AsyncRaftLogging;
import io.axor.raft.messages.ClientMessage;
import io.axor.raft.messages.PeerMessage;
import io.axor.raft.messages.PeerMessage.LeaderHeartbeat;
import io.axor.raft.messages.PeerMessage.LogAppend;
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
        raftState.setCurrentTerm(raftLogging.logEndId().term());
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
            LOG.info("Peer stage changed from {} to {}", prev, peerState);
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
        private final SequencedMap<ClientTxn, PeerMessage.ClientTxnReq> bufferedClientReqs =
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
                    peer.peerRef().tell(new LeaderHeartbeat(currentTerm, commitedId));
                }
            }, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        }

        public Map<Peer, FollowerState> getFollowerStates() {
            return followerStates;
        }

        public boolean bufferIsEmpty() {
            return bufferedClientReqs.isEmpty();
        }

        public void bufferClientCtx(PeerMessage.ClientTxnReq req) {
            var key = new ClientTxn(req.txnId(),
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
            List<LogEntry> entries = new ArrayList<>();
            LogId commitedId = raftContext.getRaftState().getCommitedId();
            int bytes = 0;
            int cnt = 0;
            while (!bufferedClientReqs.isEmpty() && cnt < entryLimit && bytes < bytesLimit) {
                var entry = bufferedClientReqs.pollFirstEntry();
                commitedId = new LogId(commitedId.index() + 1, term);
                ClientTxn key = entry.getKey();
                clientTxnList.add(key);
                PeerMessage.ClientTxnReq value = entry.getValue();
                entries.add(new LogEntry(commitedId, value.data()));
                entryLimit++;
                bytesLimit += value.data().size();
            }
            LogAppend append = new LogAppend(txnId, term, entries, commitedId);
            return new TxnContext(append, clientTxnList);
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
