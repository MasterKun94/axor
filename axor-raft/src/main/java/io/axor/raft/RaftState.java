package io.axor.raft;

import io.axor.raft.proto.PeerProto.LogId;

import java.util.List;

public class RaftState {
    private long currentTerm;
    private PeerInstance votedFor;
    private List<LogId> uncommitedId;
    private LogId commitedId;
    private PeerState peerState;
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

    public List<LogId> getUncommitedId() {
        return uncommitedId;
    }

    public void setUncommitedId(List<LogId> uncommitedId) {
        this.uncommitedId = uncommitedId;
    }

    public LogId getCommitedId() {
        return commitedId;
    }

    public void setCommitedId(LogId commitedId) {
        this.commitedId = commitedId;
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
