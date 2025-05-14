package io.axor.raft;

public enum PeerState {
    NONE,
    LEADER,
    CANDIDATE,
    FOLLOWER
}
