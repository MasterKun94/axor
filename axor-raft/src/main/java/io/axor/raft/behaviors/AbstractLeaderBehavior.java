package io.axor.raft.behaviors;

import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.raft.Peer;
import io.axor.raft.PeerState;
import io.axor.raft.RaftContext;
import io.axor.raft.messages.PeerMessage;

public class AbstractLeaderBehavior extends AbstractPeerBehavior {

    protected AbstractLeaderBehavior(RaftContext raftContext) {
        super(raftContext);
        raftContext.changeSelfPeerState(PeerState.LEADER);
        Peer leader = raftState().getLeader();
        Peer selfPeer = raftContext.getSelfPeer().peer();
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
    protected Behavior<PeerMessage> onLeaderHeartbeat(PeerMessage.LeaderHeartbeat msg) {
        if (msg.term() == raftState().getCurrentTerm()) {
            throw new IllegalStateException("Multiple leader at the same term! this should never " +
                                            "happen!");
        }
        return super.onLeaderHeartbeat(msg);
    }

    @Override
    protected Behavior<PeerMessage> onLogAppend(PeerMessage.LogAppend msg) {
        if (msg.term() == raftState().getCurrentTerm()) {
            throw new IllegalStateException("Multiple leader at the same term! this should never " +
                                            "happen!");
        }
        return super.onLogAppend(msg);
    }

    @Override
    protected Behavior<PeerMessage> onRequestVoteAck(PeerMessage.RequestVoteAck msg) {
        if (msg.term() == raftState().getCurrentTerm()) {
            return Behaviors.same();
        } else {
            return Behaviors.unhandled();
        }
    }
}
