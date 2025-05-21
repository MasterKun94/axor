package io.axor.raft.peer;

import io.axor.api.ActorRef;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.raft.PeerState;
import io.axor.raft.RaftContext;
import io.axor.raft.RaftException;
import io.axor.raft.logging.RaftLogging;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.AppendResult;
import io.axor.raft.proto.PeerProto.MediatorMessage;
import io.axor.raft.proto.PeerProto.ClientTxnReq;
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
        scheduleHeartbeatChecker = context().scheduler().scheduleSignalWithFixedDelay(
                HEARTBEAT_CHECK_SIGNAL, self(),
                interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS
        );
    }

    @Override
    protected Behavior<PeerMessage> onClientTxnReq(ClientTxnReq msg) {
        ActorRef<PeerMessage> leader = raftState().getLeader();
        long term = raftState().getCurrentTerm();
        MediatorMessage res;
        if (leader == null) {
            res = noLeaderClientMsg(msg.getSeqId(), term);
        } else {
            res = redirectClientMsg(msg.getSeqId(), leader.address(), term);
        }
        clientSender().tell(res, self());
        return Behaviors.same();
    }

    @Override
    protected Behavior<PeerMessage> onLogAppend(LogAppend msg) {
        Behavior<PeerMessage> behavior = super.onLogAppend(msg);
        if (msg.getTerm() != raftState().getCurrentTerm()) {
            return behavior;
        }
        raftState().setLatestHeartbeatTimestamp(System.currentTimeMillis());
        ActorRef<PeerMessage> sender = peerSender();
        long txnId = msg.getTxnId();
        long term = msg.getTerm();
        LogId leaderCommited = msg.getLeaderCommited();
        RaftLogging raftLogging = raftLogging();
        AppendResult.Status status;
        try {
            AppendResult res = raftLogging.append(msg.getPrevLogId(), msg.getEntriesList());
            status = res.getStatus();
            if (isAppendSuccess(status)) {
                LOG.debug("LogAppend[txnId={}, commitedId={}, peer={}] success",
                        txnId, raftLogging.commitedId(),
                        raftContext().getSelfPeer());
            } else {
                LOG.warn("LogAppend[txnId={}, commitedId={}, peer={}] failure [{}]",
                        txnId, raftLogging.commitedId(),
                        raftContext().getSelfPeer(), status);
            }
        } catch (Exception e) {
            status = AppendResult.Status.SYSTEM_ERROR;
            LOG.error("LogAppend[txnId={}, commitedId={}, peer={}] error",
                    txnId, raftLogging.commitedId(),
                    raftContext().getSelfPeer(), e);
            context().system().systemFailure(e);
        }
        LogAppendAck m = LogAppendAck.newBuilder()
                .setTxnId(txnId)
                .setTerm(term)
                .setResult(AppendResult.newBuilder()
                        .setStatus(status)
                        .setCommited(raftLogging.commitedId())
                        .addAllUncommited(raftLogging.uncommitedId()))
                .build();
        sender.tell(peerMsg(m), self());
        if (isAppendSuccess(status)) {
            LogId logEndId = raftContext().getLogEndId();
            try {
                LogId commitId = logEndId.getIndex() < leaderCommited.getIndex() ?
                        logEndId : leaderCommited;
                raftLogging.commit(commitId);
            } catch (Exception e) {
                LOG.error("LogCommit[txnId={}, commitedId={}, peer={}] after append error",
                        txnId, raftLogging.commitedId(),
                        raftContext().getSelfPeer(), e);
                context().system().systemFailure(e);
            }
        }
        return Behaviors.same();
    }

    @Override
    protected Behavior<PeerMessage> onLeaderHeartbeat(LeaderHeartbeat msg) {
        Behavior<PeerMessage> behavior = super.onLeaderHeartbeat(msg);
        if (msg.getTerm() != raftState().getCurrentTerm()) {
            return behavior;
        }
        raftState().setLatestHeartbeatTimestamp(System.currentTimeMillis());
        if (raftLogging().commitedId().equals(msg.getLeaderCommited())) {
            return Behaviors.same();
        }
        tryCommit(msg.getLeaderCommited());
        return Behaviors.same();
    }

    private void tryCommit(LogId leaderCommited) {
        RaftLogging raftLogging = raftLogging();
        try {
            PeerProto.CommitResult.Status status = raftLogging.commit(leaderCommited).getStatus();
            if (isCommitSuccess(status)) {
                LOG.debug("LogCommit[commitedId={}, peer={}] success",
                        raftLogging.commitedId(), raftContext().getSelfPeer());
            } else {
                LOG.warn("LogCommit[commitedId={}, peer={}] failure status {}",
                        raftLogging.commitedId(), raftContext().getSelfPeer(), status);
            }
        } catch (RaftException e) {
            LOG.error("LogCommit[commitedId={}, peer={}] error",
                    raftLogging.commitedId(), raftContext().getSelfPeer(), e);
        }
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
