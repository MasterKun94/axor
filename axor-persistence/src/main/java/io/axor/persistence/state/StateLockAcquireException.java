package io.axor.persistence.state;

public class StateLockAcquireException extends Exception {
    public StateLockAcquireException() {
    }

    public StateLockAcquireException(String message) {
        super(message);
    }

    public StateLockAcquireException(String message, Throwable cause) {
        super(message, cause);
    }

    public StateLockAcquireException(Throwable cause) {
        super(cause);
    }

    public StateLockAcquireException(String message, Throwable cause, boolean enableSuppression,
                                     boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
