package io.masterkun.kactor.cluster;

public enum LocalMemberState {
    NONE,
    JOINING,
    UP,
    WEEKLY_UP,
    DISCONNECTED,
    LEAVING,
    LEFT
}
