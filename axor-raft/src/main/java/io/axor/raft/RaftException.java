package io.axor.raft;

public class RaftException extends Exception {
    public RaftException() {
    }

    public RaftException(String message) {
        super(message);
    }

    public RaftException(String message, Throwable cause) {
        super(message, cause);
    }

    public RaftException(Throwable cause) {
        super(cause);
    }
}
