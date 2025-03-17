package io.masterkun.kactor.runtime;

public enum StatusCode {
    COMPLETE(0, true),
    SYSTEM_ERROR(100, false),
    UNAVAILABLE(101, false),
    CANCELLED(102, false),
    UNAUTHENTICATED(103, false),

    ACTOR_NOT_FOUND(104, false),
    MSG_TYPE_MISMATCH(105, false),
    SERDE_MISMATCH(106, false),

    UNKNOWN(-1, false),
    ;

    public final int code;
    public final boolean success;

    StatusCode(int code, boolean success) {
        this.code = code;
        this.success = success;
    }

    public static StatusCode fromCode(int code) {
        for (StatusCode status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return UNKNOWN;
    }

    public Status toStatus() {
        return new Status(code, null);
    }

    public Status toStatus(Throwable ex) {
        return new Status(code, ex);
    }
}
