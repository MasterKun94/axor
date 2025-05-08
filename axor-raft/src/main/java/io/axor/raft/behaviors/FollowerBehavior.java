package io.axor.raft.behaviors;

import io.axor.api.ActorRef;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.api.impl.ActorUnsafe;
import io.axor.raft.AppendStatus;
import io.axor.raft.LogId;
import io.axor.raft.PeerState;
import io.axor.raft.RaftContext;
import io.axor.raft.messages.PeerMessage;
import io.axor.raft.messages.PeerMessage.LogAppend;
import io.axor.raft.messages.PeerMessage.LogAppendAck;
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
    protected Behavior<PeerMessage> onLogAppend(LogAppend msg) {
        if (msg.term() != raftState().getCurrentTerm()) {
            return super.onLogAppend(msg);
        }
        raftState().setLatestHeartbeatTimestamp(System.currentTimeMillis());
        ActorRef<PeerMessage> sender = peerSender();
        long txnId = msg.txnId();
        long term = msg.term();
        LogId leaderCommited = msg.leaderCommited();
        logAppend(msg).observe((s, e) -> {
            AppendStatus status;
            if (e != null) {
                status = AppendStatus.SYSTEM_ERROR;
                LOG.error("LogAppend[txnId={}, commitedId={}, peer={}] error",
                        txnId, raftState().getCommitedId(),
                        raftContext().getSelfPeer().peer(), e);
            } else {
                status = s.status();
                if (status.isSuccess()) {
                    LOG.debug("LogAppend[txnId={}, commitedId={}, peer={}] success",
                            txnId, raftState().getCommitedId(),
                            raftContext().getSelfPeer().peer());
                } else {
                    LOG.warn("LogAppend[txnId={}, commitedId={}, peer={}] failure [{}]",
                            txnId, raftState().getCommitedId(),
                            raftContext().getSelfPeer().peer(), s);
                }
            }
            LogAppendAck m = new LogAppendAck(txnId, term, status,
                    raftState().getCommitedId(), raftState().getUncommitedId());
            sender.tell(m, self());
            if (status.isSuccess()) {
                LogId logEndId = raftContext().getLogEndId();
                logCommit(logEndId.index() < leaderCommited.index() ? logEndId : leaderCommited);
            }
        });
        return Behaviors.same();
    }

    @Override
    protected Behavior<PeerMessage> onLeaderHeartbeat(PeerMessage.LeaderHeartbeat msg) {
        LOG.debug("Receive leader heartbeat {}", msg);
        if (msg.term() != raftState().getCurrentTerm()) {
            return super.onLeaderHeartbeat(msg);
        }
        raftState().setLatestHeartbeatTimestamp(System.currentTimeMillis());
        if (raftState().getCommitedId().equals(msg.leaderCommited())) {
            return Behaviors.same();
        }
        tryCommit(msg.leaderCommited());
        return super.onLeaderHeartbeat(msg);
    }

    private void tryCommit(LogId leaderCommited) {
        logCommit(leaderCommited).observe((m, e) -> {
            if (e != null) {
                LOG.error("LogCommit[commitedId={}, peer={}] error",
                        raftState().getCommitedId(), raftContext().getSelfPeer().peer(), e);
            } else if (m.status().isSuccess()) {
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
