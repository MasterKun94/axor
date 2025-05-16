package io.axor.raft.kvstore.sample;

import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteOptions;

public class RocksDBExample {
    private static final String dbPath = ".tmp/data/";

    static {
        RocksDB.loadLibrary();
    }

    public static void main(String[] args) {
        try (final Options options = new Options().setCreateIfMissing(true);
             final RocksDB db = RocksDB.open(options, dbPath)) {
// 写入数据
            WriteOptions opt = new WriteOptions();
            db.put(opt, "hello".getBytes(), "world".getBytes());
            db.put(opt, "hello.1".getBytes(), "HiHi".getBytes());
            db.put(opt, "hello.2".getBytes(), "HaHa".getBytes());

// 读取数据
            byte[] value = db.get("hello".getBytes());
            System.out.println("Get('hello') = " + new String(value));

// 迭代数据
            try (final RocksIterator iter = db.newIterator()) {
                iter.seek("hello".getBytes());
                for (; iter.isValid(); iter.next()) {
                    System.out.println("key: " + new String(iter.key()) + ", value: " + new String(iter.value()));
                }
                iter.seek("hello.1".getBytes());
                for (; iter.isValid(); iter.next()) {
                    System.out.println("key: " + new String(iter.key()) + ", value: " + new String(iter.value()));
                }
            }
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
    }
}
