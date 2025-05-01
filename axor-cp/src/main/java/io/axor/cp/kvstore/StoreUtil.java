package io.axor.cp.kvstore;

import io.axor.cp.kvstore.exception.RocksDBStoreException;
import io.axor.cp.kvstore.exception.StoreException;
import org.rocksdb.RocksDBException;

public class StoreUtil {
    public static StoreException convert(RocksDBException ex) {
        return new RocksDBStoreException(ex);
    }
}
