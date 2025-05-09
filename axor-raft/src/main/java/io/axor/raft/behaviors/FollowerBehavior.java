package io.axor.raft.behaviors;

import com.google.protobuf.ByteString;
import io.axor.api.ActorRef;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.api.impl.ActorUnsafe;
import io.axor.raft.PeerInstance;
import io.axor.raft.PeerState;
import io.axor.raft.RaftContext;
import io.axor.raft.proto.PeerProto.AppendResult;
import io.axor.raft.proto.PeerProto.ClientTxnReq;
import io.axor.raft.proto.PeerProto.ClientTxnRes;
import io.axor.raft.proto.PeerProto.LeaderHeartbeat;
import io.axor.raft.proto.PeerProto.LogAppend;
import io.axor.raft.proto.PeerProto.LogAppendAck;
import io.axor.raft.proto.PeerProto.LogId;
import io.axor.raft.proto.PeerProto.PeerMessage;
import io.axor.runtime.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class FollowerBehavior extends AbstractPeerBehavior {
    private static final Logger LOG = LoggerFactory.getLogger(FollowerBehavior.class);
    private static final Signal HEARTBEAT_CHECK_SIGNAL = new Signal() {
        @Override
        public String toString() {
            return "HEARTBEAT_CHECK_SIGNAL";
        }
    };
    private final ScheduledFuture<?> scheduleHeartbeatChecker;

    public FollowerBehavior(RaftContext raftContext) {
        super(raftContext);
        raftContext.changeSelfPeerState(PeerState.FOLLOWER);
        raftState().setLatestHeartbeatTimestamp(System.currentTimeMillis());
        Duration interval = config().leaderHeartbeatInterval();
        scheduleHeartbeatChecker = context().scheduler().scheduleWithFixedDelay(() -> {
            ActorUnsafe.signalInline(self(), HEARTBEAT_CHECK_SIGNAL);
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    protected Behavior<PeerMessage> onClientTxnReq(ClientTxnReq msg) {
        PeerInstance leader = raftState().getLeader();
        ClientTxnRes res;
        if (leader == null) {
            res = ClientTxnRes.newBuilder()
                    .setTxnId(msg.getTxnId())
                    .setStatus(ClientTxnRes.Status.NO_LEADER)
                    .build();
        } else {
            ByteString data = ByteString.copyFromUtf8(Integer.toString(leader.peer().id()));
            res = ClientTxnRes.newBuilder()
                    .setTxnId(msg.getTxnId())
                    .setStatus(ClientTxnRes.Status.REDIRECT)
                    .setData(data)
                    .build();
        }
        clientSender().tell(clientMsg(res), self());
        return Behaviors.same();
    }

    @Override
    protected Behavior<PeerMessage> onLogAppend(LogAppend msg) {
        if (msg.getTerm() != raftState().getCurrentTerm()) {
            return super.onLogAppend(msg);
        }
        raftState().setLatestHeartbeatTimestamp(System.currentTimeMillis());
        ActorRef<PeerMessage> sender = peerSender();
        long txnId = msg.getTxnId();
        long term = msg.getTerm();
        LogId leaderCommited = msg.getLeaderCommited();
        logAppend(msg).observe((s, e) -> {
            AppendResult.Status status;
            if (e != null) {
                status = AppendResult.Status.SYSTEM_ERROR;
                LOG.error("LogAppend[txnId={}, commitedId={}, peer={}] error",
                        txnId, raftState().getCommitedId(),
                        raftContext().getSelfPeer().peer(), e);
            } else {
                status = s.getStatus();
                if (isAppendSuccess(status)) {
                    LOG.debug("LogAppend[txnId={}, commitedId={}, peer={}] success",
                            txnId, raftState().getCommitedId(),
                            raftContext().getSelfPeer().peer());
                } else {
                    LOG.warn("LogAppend[txnId={}, commitedId={}, peer={}] failure [{}]",
                            txnId, raftState().getCommitedId(),
                            raftContext().getSelfPeer().peer(), s);
                }
            }
            LogAppendAck m = LogAppendAck.newBuilder()
                    .setTxnId(txnId)
                    .setTerm(term)
                    .setResult(AppendResult.newBuilder()
                            .setStatus(status)
                            .setCommited(raftState().getCommitedId())
                            .addAllUncommited(raftState().getUncommitedId()))
                    .build();
            sender.tell(peerMsg(m), self());
            if (isAppendSuccess(status)) {
                LogId logEndId = raftContext().getLogEndId();
                logCommit(logEndId.getIndex() < leaderCommited.getIndex() ? logEndId :
                        leaderCommited);
            }
        });
        return Behaviors.same();
    }

    @Override
    protected Behavior<PeerMessage> onLeaderHeartbeat(LeaderHeartbeat msg) {
        LOG.debug("Receive leader heartbeat {}", msg);
        if (msg.getTerm() != raftState().getCurrentTerm()) {
            return super.onLeaderHeartbeat(msg);
        }
        raftState().setLatestHeartbeatTimestamp(System.currentTimeMillis());
        if (raftState().getCommitedId().equals(msg.getLeaderCommited())) {
            return Behaviors.same();
        }
        tryCommit(msg.getLeaderCommited());
        return super.onLeaderHeartbeat(msg);
    }

    private void tryCommit(LogId leaderCommited) {
        logCommit(leaderCommited).observe((m, e) -> {
            if (e != null) {
                LOG.error("LogCommit[commitedId={}, peer={}] error",
                        raftState().getCommitedId(), raftContext().getSelfPeer().peer(), e);
            } else if (isCommitSuccess(m.getStatus())) {
                LOG.debug("LogCommit[commitedId={}, peer={}] success",
                        raftState().getCommitedId(), raftContext().getSelfPeer().peer());
            } else {
                LOG.warn("LogCommit[commitedId={}, peer={}] failure status {}",
                        raftState().getCommitedId(), raftContext().getSelfPeer().peer(), m);
            }
        });
    }

    @Override
    protected Behavior<PeerMessage> onSignal(Signal signal) {
        if (signal == HEARTBEAT_CHECK_SIGNAL) {
            Duration timeout = config().leaderHeartbeatTimeout();
            long ts = raftState().getLatestHeartbeatTimestamp();
            if (System.currentTimeMillis() - ts > timeout.toMillis()) {
                LOG.warn("Leader heartbeat timeout, start candidate");
                return new CandidateBehavior(raftContext());
            }
            return Behaviors.same();
        }
        return super.onSignal(signal);
    }

    @Override
    protected void onBehaviorChanged() {
        scheduleHeartbeatChecker.cancel(false);
    }
}
