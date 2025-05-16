package io.axor.raft.kvstore;

import io.axor.raft.kvstore.exception.RocksDBStoreException;
import io.axor.raft.kvstore.exception.StoreException;
import org.rocksdb.RocksDBException;

public class StoreUtil {
    public static StoreException convert(RocksDBException ex) {
        return new RocksDBStoreException(ex);
    }
}
