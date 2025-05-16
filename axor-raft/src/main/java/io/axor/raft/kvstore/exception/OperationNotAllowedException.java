package io.axor.raft.kvstore.exception;

public final class OperationNotAllowedException extends StoreException {
    public OperationNotAllowedException(String message) {
        super(message);
    }
}
