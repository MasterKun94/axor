package io.axor.raft.kvstore.exception;

import java.io.IOException;

public abstract sealed class StoreException extends IOException permits ParentNotFoundException,
        NodeNotFoundException, NodeAlreadyExistsException, OperationNotAllowedException,
        RocksDBStoreException {
    public StoreException() {
    }

    public StoreException(String message) {
        super(message);
    }

    public StoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public StoreException(Throwable cause) {
        super(cause);
    }
}
