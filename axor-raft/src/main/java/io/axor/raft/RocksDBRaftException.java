package io.axor.raft;

import org.rocksdb.RocksDBException;
import org.rocksdb.Status;

public class RocksDBRaftException extends RaftException {
    private final Status status;

    public RocksDBRaftException(RocksDBException cause) {
        super(cause);
        status = cause.getStatus();
    }

    public RocksDBRaftException(Status status) {
        super(status.getState() != null ? status.getState() : status.getCodeString());
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }
}
