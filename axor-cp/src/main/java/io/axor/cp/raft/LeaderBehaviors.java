package io.axor.cp.raft;

import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.api.impl.ActorUnsafe;
import io.axor.commons.config.MemorySize;
import io.axor.cp.messages.AppendStatus;
import io.axor.cp.messages.ClientMessage;
import io.axor.cp.messages.ClientTxnReq;
import io.axor.cp.messages.ClientTxnRes;
import io.axor.cp.messages.CommitStatus;
import io.axor.cp.messages.LeaderState;
import io.axor.cp.messages.LogAppend;
import io.axor.cp.messages.LogAppendAck;
import io.axor.cp.messages.LogCommit;
import io.axor.cp.messages.LogCommitAck;
import io.axor.cp.messages.LogEntry;
import io.axor.cp.messages.LogEntryId;
import io.axor.cp.messages.Peer;
import io.axor.cp.messages.RaftMessage;
import io.axor.cp.messages.RequestVote;
import io.axor.cp.messages.ResetUncommitedSignal;
import io.axor.runtime.Signal;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class LeaderBehaviors {
    private static final Logger LOG = LoggerFactory.getLogger(LeaderBehaviors.class);

    private final RaftContext raftContext;
    private final ActorContext<RaftMessage> context;
    private final ClientTxnBuffer clientTxnBuffer = new ClientTxnBuffer();
    private final ScheduledFuture<?> scheduledHeartbeat;
    private long nextTxnId = 0;

    public LeaderBehaviors(RaftContext raftContext, ActorContext<RaftMessage> context) {
        this.raftContext = raftContext;
        this.context = context;
        long interval = raftContext.config().leaderHeartbeatInterval().toMillis();
        this.scheduledHeartbeat = context.dispatcher().scheduleWithFixedDelay(() -> {
            check();
            for (Peer peer : raftContext.peers()) {
                if (raftContext.isLeader(peer)) {
                    continue;
                }
                raftContext.peerRef(peer).tell(new LeaderState(raftContext.commitedId()));
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    private void check() {
        assert raftContext.selfPeer().equals(raftContext.leaderPeer());
    }

    public Behavior<RaftMessage> idle() {
        check();
        return (context, m) -> {
            if (m instanceof ClientTxnReq c) {
                clientTxnBuffer.append(c, context.sender(ClientMessage.class));
                return preAppend();
            } else if (m instanceof RequestVote(var id)) {
                return receiveRequestVote(id, null);
            }
            return Behaviors.same();
        };
    }

    private Behavior<RaftMessage> preAppend() {
        check();
        long txnId = nextTxnId++;
        LogEntryId currentCommited = raftContext.commitedId();
        List<LogEntry> entries = new ArrayList<>();
        List<ClientTxnCtx> ctc = new ArrayList<>();
        LogEntryId commitId = null;
        MemorySize logAppendBytesLimit = raftContext.config().logAppendSizeLimit();
        var iter = clientTxnBuffer.poll(logAppendBytesLimit.toInt());
        while (iter.hasNext()) {
            var entry = iter.next();
            LogEntryId id = currentCommited.next();
            commitId = id;
            entries.add(new LogEntry(id, entry.getValue().data()));
            ctc.add(entry.getKey());
        }
        LogAppend req = new LogAppend(txnId, entries, currentCommited);
        for (Peer peer : raftContext.peers()) {
            if (raftContext.isLeader(peer)) {
                raftContext.replicatorRef(peer).tell(req, context.self());
            } else {
                raftContext.peerRef(peer).tell(req, context.self());
            }
        }
        var f = context.dispatcher().schedule(() -> {
            ActorUnsafe.signal(context.self(), new AppendTimeoutSignal(txnId));
        }, raftContext.config().logAppendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        TxnCtx txnCtx = new TxnCtx(txnId, commitId, ctc, f);
        return append(txnCtx, 0, 0, false);
    }

    private Behavior<RaftMessage> append(TxnCtx txnCtx, int successCnt, int failureCnt,
                                         Boolean leaderSus) {
        check();
        if (leaderSus != null && leaderSus && successCnt >= raftContext.majorityCount()) {
            txnCtx.timeoutFuture.cancel(false);
            return commit(txnCtx);
        }
        if (leaderSus != null && (!leaderSus || failureCnt >= raftContext.majorityCount())) {
            txnCtx.timeoutFuture.cancel(false);
            return rollback(txnCtx);
        }
        return Behaviors.receive(m -> {
            if (m instanceof LogAppendAck(var id, var status, var ignore) && id == txnCtx.id) {
                if (status == AppendStatus.SUCCESS) {
                    return raftContext.isLeader(context.sender()) ?
                            append(txnCtx, successCnt + 1, failureCnt, true) :
                            append(txnCtx, successCnt + 1, failureCnt, leaderSus);
                } else {
                    return raftContext.isLeader(context.sender()) ?
                            append(txnCtx, successCnt, failureCnt + 1, false) :
                            append(txnCtx, successCnt, failureCnt + 1, leaderSus);
                }
            } else if (m instanceof ClientTxnReq c) {
                clientTxnBuffer.append(c, context.sender(ClientMessage.class));
                return Behaviors.same();
            } else if (m instanceof RequestVote(var id)) {
                return receiveRequestVote(id, txnCtx);
            }
            return Behaviors.unhandled();
        }, signal -> {
            if (signal instanceof AppendTimeoutSignal(var id) && id == txnCtx.id) {
                return rollback(txnCtx);
            }
            return Behaviors.unhandled();
        });
    }

    private Behavior<RaftMessage> commit(TxnCtx txnCtx) {
        check();
        LogCommit logCommit = new LogCommit(txnCtx.id, txnCtx.commitId, raftContext.commitedId());
        raftContext.peerRef(raftContext.selfPeer()).tell(logCommit, context.self());
        return Behaviors.receiveMessage(m -> {
            if (m instanceof LogCommitAck ack && ack.txnId() == txnCtx.id) {
                if (ack.status() == CommitStatus.SUCCESS) {
                    check();
                    assert raftContext.commitedId().equals(txnCtx.commitId);
                    if (clientTxnBuffer.isEmpty()) {
                        for (Peer peer : raftContext.peers()) {
                            if (raftContext.isLeader(peer)) {
                                continue;
                            }
                            raftContext.peerRef(peer).tell(new LeaderState(raftContext.commitedId()));
                        }
                        return idle();
                    } else {
                        return preAppend();
                    }
                } else {
                    return rollback(txnCtx);
                }
            } else if (m instanceof ClientTxnReq c) {
                clientTxnBuffer.append(c, context.sender(ClientMessage.class));
                return Behaviors.same();
            } else if (m instanceof RequestVote(var id)) {
                return receiveRequestVote(id, txnCtx);
            }
            return Behaviors.unhandled();
        });
    }

    private Behavior<RaftMessage> rollback(TxnCtx txnCtx) {

        if (clientTxnBuffer.isEmpty()) {
            return idle();
        } else {
            return preAppend();
        }
    }

    private Behavior<RaftMessage> receiveRequestVote(LogEntryId candidateId,
                                                     @Nullable TxnCtx pendingTxnCtx) {
        if (candidateId.term() > raftContext.commitedId().term()) {
            scheduledHeartbeat.cancel(false);
            if (pendingTxnCtx != null) {
                for (ClientTxnCtx clientTxnCtx : pendingTxnCtx.clientTxnCtxList) {
                    var msg = new ClientTxnRes(pendingTxnCtx.id, false, "Leader changed");
                    clientTxnCtx.sender.tell(msg, context.self());
                }
            }
            var selfReplicator = raftContext.replicatorRef(raftContext.selfPeer());
            ActorUnsafe.signalInline(selfReplicator, new ResetUncommitedSignal());
            int threshold = raftContext.config().followerIndexLagThreshold();
            raftContext.updateLeader(context.sender());
            if (candidateId.index() - raftContext.commitedId().index() > threshold) {
                return new FollowerBehaviors(raftContext, context).lag();
            } else {
                return new FollowerBehaviors(raftContext, context).chase();
            }
        } else {
            context.sender(RaftMessage.class)
                    .tell(new LeaderState(raftContext.commitedId()));
            return Behaviors.same();
        }
    }

    private record AppendTimeoutSignal(long txnId) implements Signal {
    }

    private record ClientTxnCtx(ActorRef<ClientMessage> sender, long clientTxnId) {
    }

    private record TxnCtx(long id, LogEntryId commitId, List<ClientTxnCtx> clientTxnCtxList,
                          ScheduledFuture<?> timeoutFuture) {
    }

    private static class ClientTxnBuffer {
        private final SequencedMap<ClientTxnCtx, ClientTxnReq> map = new LinkedHashMap<>();

        public void append(ClientTxnReq req, ActorRef<ClientMessage> sender) {
            map.put(new ClientTxnCtx(sender, req.clientTxnId()), req);
        }

        public Iterator<Map.Entry<ClientTxnCtx, ClientTxnReq>> poll(int maxMsgSizeLimit) {
            return new Iterator<>() {
                private int size = 0;

                @Override
                public boolean hasNext() {
                    return !map.isEmpty() && size < maxMsgSizeLimit;
                }

                @Override
                public Map.Entry<ClientTxnCtx, ClientTxnReq> next() {
                    var entry = map.pollFirstEntry();
                    size += entry.getValue().data().size();
                    return entry;
                }
            };
        }

        public boolean isEmpty() {
            return map.isEmpty();
        }
    }
}
