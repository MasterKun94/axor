package io.axor.raft;

public class RaftState {
    private long currentTerm;
    private PeerInstance votedFor;
    private PeerState peerState = PeerState.NONE;
    private long latestHeartbeatTimestamp;
    private PeerInstance leader;

    public long getCurrentTerm() {
        return currentTerm;
    }

    public void setCurrentTerm(long currentTerm) {
        this.currentTerm = currentTerm;
    }

    public PeerInstance getVotedFor() {
        return votedFor;
    }

    public void setVotedFor(PeerInstance votedFor) {
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

    public PeerInstance getLeader() {
        return leader;
    }

    public void setLeader(PeerInstance leader) {
        this.leader = leader;
    }
}
