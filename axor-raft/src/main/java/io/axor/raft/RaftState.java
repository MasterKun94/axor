package io.axor.raft;

import io.axor.api.ActorRef;
import io.axor.raft.proto.PeerProto.PeerMessage;

public class RaftState {
    private long currentTerm;
    private ActorRef<PeerMessage> votedFor;
    private PeerState peerState = PeerState.NONE;
    private long latestHeartbeatTimestamp;
    private ActorRef<PeerMessage> leader;

    public long getCurrentTerm() {
        return currentTerm;
    }

    public void setCurrentTerm(long currentTerm) {
        this.currentTerm = currentTerm;
    }

    public ActorRef<PeerMessage> getVotedFor() {
        return votedFor;
    }

    public void setVotedFor(ActorRef<PeerMessage> votedFor) {
        this.votedFor = votedFor;
    }

    public PeerState getPeerState() {
        return peerState;
    }

    public void setPeerState(PeerState peerState) {
        this.peerState = peerState;
    }

    public long getLatestHeartbeatTimestamp() {
        return latestHeartbeatTimestamp;
    }

    public void setLatestHeartbeatTimestamp(long latestHeartbeatTimestamp) {
        this.latestHeartbeatTimestamp = latestHeartbeatTimestamp;
    }

    public ActorRef<PeerMessage> getLeader() {
        return leader;
    }

    public void setLeader(ActorRef<PeerMessage> leader) {
        this.leader = leader;
    }
}
