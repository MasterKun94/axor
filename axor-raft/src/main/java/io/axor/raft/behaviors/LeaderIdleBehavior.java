package io.axor.raft.behaviors;

import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.raft.AppendStatus;
import io.axor.raft.LogId;
import io.axor.raft.PeerInstance;
import io.axor.raft.RaftContext;
import io.axor.raft.RaftContext.FollowerState;
import io.axor.raft.messages.PeerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeaderIdleBehavior extends AbstractLeaderBehavior {
    private static final Logger LOG = LoggerFactory.getLogger(LeaderIdleBehavior.class);

    protected LeaderIdleBehavior(RaftContext raftContext) {
        super(raftContext);
    }

    @Override
    protected Behavior<PeerMessage> onClientTxn(PeerMessage.ClientTxnReq msg) {
        leaderContext().bufferClientCtx(msg);
        return new LeaderInTxnBehavior(raftContext());
    }

    @Override
    protected Behavior<PeerMessage> onLogAppendAck(PeerMessage.LogAppendAck msg) {
        PeerInstance peerOfSender = raftContext().getPeerOfSender();
        FollowerState followerState = leaderContext().getFollowerStates().get(peerOfSender.peer());
        if (followerState.getLatestTxnId() != msg.txnId()) {
            // ignore
            return Behaviors.same();
        }
        followerState.setCommited(msg.commited());
        followerState.setUncommited(msg.uncommited());
        LogId commitedId = raftState().getCommitedId();
        long term = raftState().getCurrentTerm();
        if (!peerOfSender.isSelf()) {
            // follower存在延迟，还要继续追加写数据
            if ((msg.success() || msg.status() == AppendStatus.INDEX_EXCEEDED) &&
                msg.logEndId().index() < commitedId.index()) {
                logReadForSync(followerState.getCommited(), followerState.getUncommited())
                        .observe((l, e) -> {
                            if (e != null) {
                                LOG.error("Read for sync error, {} state suspicious",
                                        peerOfSender.peer(), e);
                                return;
                            }
                            var m = new PeerMessage.LogAppend(msg.txnId(), term, l, commitedId);
                            peerOfSender.peerRef().tell(m, self());
                        });
                return Behaviors.same();
            }
        }
        return Behaviors.same();
    }
}
