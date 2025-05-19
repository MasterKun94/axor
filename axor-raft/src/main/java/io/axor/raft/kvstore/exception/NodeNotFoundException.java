package io.axor.raft.kvstore.exception;

public final class NodeNotFoundException extends StoreException {
    public NodeNotFoundException(String message) {
        super(message);
    }
}
