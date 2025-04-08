package io.axor.cluster;

public enum LocalMemberState {
    NONE,
    JOINING,
    HEALTHY,
    UNHEALTHY,
    ORPHANED,
    LEAVING,
    LEFT
}
