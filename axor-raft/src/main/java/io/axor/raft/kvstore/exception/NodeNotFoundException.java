package io.axor.raft.kvstore.exception;

import io.axor.raft.kvstore.exception.StoreException;

public final class NodeNotFoundException extends StoreException {
    public NodeNotFoundException(String message) {
        super(message);
    }
}
