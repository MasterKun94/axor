package io.axor.raft;

public enum AppendStatus {
    SUCCESS(true),
    NO_ACTION(true),
    TERM_DENY(false),
    SYSTEM_ERROR(false),
    TERM_EXPIRED(false),
    INDEX_EXPIRED(false),
    INDEX_EXCEEDED(false);

    private final boolean success;

    AppendStatus(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }
}
