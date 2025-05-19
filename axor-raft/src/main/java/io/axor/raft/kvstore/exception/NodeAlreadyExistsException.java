package io.axor.raft.kvstore.exception;

public final class NodeAlreadyExistsException extends StoreException {
    public NodeAlreadyExistsException(String message) {
        super(message);
    }
}
