package io.axor.raft;

public enum ClientTxnStatus {
    SUCCESS,
    NO_LEADER,
    REDIRECT,
    SYS_ERROR,
    APPEND_TIMEOUT,
    APPEND_FAILURE,
    COMMIT_FAILURE
}
