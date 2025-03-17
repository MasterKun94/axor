package io.masterkun.axor.cluster.membership;

public enum MemberAction {
    JOIN,
    UPDATE,
    HEARTBEAT,

    LEAVE,
    LEAVE_ACK,

    SUSPECT,
    STRONG_SUSPECT,
    FAIL,
    REMOVE,
}
