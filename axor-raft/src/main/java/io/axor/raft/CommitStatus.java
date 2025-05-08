package io.axor.raft;

public enum CommitStatus {
    SUCCESS(true),
    NO_ACTION(true),
    NO_VALUE(false),
    SYSTEM_ERROR(false),
    NOT_ENOUGH_DATA(false),
    ILLEGAL_STATE(false),
    INDEX_EXCEEDED(false);


    private final boolean success;

    CommitStatus(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }
}
