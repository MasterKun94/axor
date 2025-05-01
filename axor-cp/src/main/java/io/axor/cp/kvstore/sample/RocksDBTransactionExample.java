package io.axor.cp.kvstore.sample;

import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;

public class RocksDBTransactionExample {
    private static final String txdbPath = "./data-tx/";

    static {
        RocksDB.loadLibrary();
    }

    public static void main(String[] args) {
        try (final Options options = new Options().setCreateIfMissing(true);
             final OptimisticTransactionDB txnDb = OptimisticTransactionDB.open(options,
                     txdbPath)) {

            try (final WriteOptions writeOptions = new WriteOptions();
                 final ReadOptions readOptions = new ReadOptions()) {

// 开始事务
                try (final Transaction txn = txnDb.beginTransaction(writeOptions)) {
                    byte[] key = "abc".getBytes();
                    byte[] value = "def".getBytes();

// 在事务中写入数据
                    txn.put(key, value);

// 提交事务
                    txn.commit();
                }
            }
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
    }
}
