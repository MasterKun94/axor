package io.axor.raft.behaviors;

import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.raft.PeerInstance;
import io.axor.raft.PeerState;
import io.axor.raft.RaftContext;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.PeerMessage;

public class AbstractLeaderBehavior extends AbstractPeerBehavior {

    protected AbstractLeaderBehavior(RaftContext raftContext) {
        super(raftContext);
        raftContext.changeSelfPeerState(PeerState.LEADER);
        PeerInstance leader = raftState().getLeader();
        PeerInstance selfPeer = raftContext.getSelfPeer();
        if (leader == null) {
            raftState().setLeader(selfPeer);
        } else if (!leader.equals(selfPeer)) {
            throw new IllegalStateException("should never happen");
        }
    }

    protected RaftContext.LeaderContext leaderContext() {
        return raftContext().getLeaderContext();
    }

    @Override
    protected Behavior<PeerMessage> onLeaderHeartbeat(PeerProto.LeaderHeartbeat msg) {
        if (msg.getTerm() == raftState().getCurrentTerm()) {
            throw new IllegalStateException("Multiple leader at the same term! this should never " +
                                            "happen!");
        }
        return super.onLeaderHeartbeat(msg);
    }

    @Override
    protected Behavior<PeerMessage> onLogAppend(PeerProto.LogAppend msg) {
        if (msg.getTerm() == raftState().getCurrentTerm()) {
            throw new IllegalStateException("Multiple leader at the same term! this should never " +
                                            "happen!");
        }
        return super.onLogAppend(msg);
    }

    @Override
    protected Behavior<PeerMessage> onRequestVoteAck(PeerProto.RequestVoteAck msg) {
        if (msg.getTerm() == raftState().getCurrentTerm()) {
            return Behaviors.same();
        } else {
            return Behaviors.unhandled();
        }
    }

    protected boolean needContinueAppend(PeerProto.AppendResult result) {
        PeerProto.AppendResult.Status status = result.getStatus();
        if (status == PeerProto.AppendResult.Status.SUCCESS ||
            status == PeerProto.AppendResult.Status.NO_ACTION ||
            status == PeerProto.AppendResult.Status.INDEX_EXCEEDED) {
            int cnt = result.getUncommitedCount();
            PeerProto.LogId logEndId = cnt > 0 ? result.getUncommited(cnt - 1) :
                    result.getCommited();
            return logEndId.getIndex() < raftState().getCommitedId().getIndex();
        } else {
            return false;
        }
    }
}
