package io.axor.raft.kvstore.exception;

import io.axor.raft.kvstore.exception.StoreException;
import org.rocksdb.RocksDBException;
import org.rocksdb.Status;

public final class RocksDBStoreException extends StoreException {
    private final Status status;

    public RocksDBStoreException(RocksDBException cause) {
        super(cause);
        status = cause.getStatus();
    }

    public RocksDBStoreException(Status status) {
        super(status.getState() != null ? status.getState() : status.getCodeString());
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }
}
