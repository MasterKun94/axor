package io.axor.raft.kvstore.exception;

public final class ParentNotFoundException extends StoreException {
    public ParentNotFoundException(String message) {
        super(message);
    }
}
