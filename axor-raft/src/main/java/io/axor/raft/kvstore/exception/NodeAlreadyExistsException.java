package io.axor.raft.kvstore.exception;

import io.axor.raft.kvstore.exception.StoreException;

public final class NodeAlreadyExistsException extends StoreException {
    public NodeAlreadyExistsException(String message) {
        super(message);
    }
}
