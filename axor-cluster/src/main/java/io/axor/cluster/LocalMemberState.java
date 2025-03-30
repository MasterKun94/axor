package io.axor.cluster;

public enum LocalMemberState {
    NONE,
    JOINING,
    UP,
    WEAKLY_UP,
    DISCONNECTED,
    LEAVING,
    LEFT
}
