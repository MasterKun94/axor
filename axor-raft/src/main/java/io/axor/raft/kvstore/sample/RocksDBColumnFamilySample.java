package io.axor.raft.kvstore.sample;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import java.util.ArrayList;
import java.util.List;

public class RocksDBColumnFamilySample {
    static {
        RocksDB.loadLibrary();
    }

    public static void main(final String[] args) throws RocksDBException {
        if (args.length < 1) {
            System.out.println(
                    "usage: RocksDBColumnFamilySample db_path");
            System.exit(-1);
        }

        final String db_path = args[0];

        System.out.println("RocksDBColumnFamilySample");
        try (final Options options = new Options().setCreateIfMissing(true);
             final RocksDB db = RocksDB.open(options, db_path)) {

            assert (db != null);

            // create column family
            try (final ColumnFamilyHandle columnFamilyHandle = db.createColumnFamily(
                    new ColumnFamilyDescriptor("new_cf".getBytes(),
                            new ColumnFamilyOptions()))) {
                assert (columnFamilyHandle != null);
            }
        }

        // open DB with two column families
        final List<ColumnFamilyDescriptor> columnFamilyDescriptors =
                new ArrayList<>();
        // have to open default column family
        columnFamilyDescriptors.add(new ColumnFamilyDescriptor(
                RocksDB.DEFAULT_COLUMN_FAMILY, new ColumnFamilyOptions()));
        // open the new one, too
        columnFamilyDescriptors.add(new ColumnFamilyDescriptor(
                "new_cf".getBytes(), new ColumnFamilyOptions()));
        final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
        try (final DBOptions options = new DBOptions();
             final RocksDB db = RocksDB.open(options, db_path,
                     columnFamilyDescriptors, columnFamilyHandles)) {
            assert (db != null);

            try {
                // put and get from non-default column family
                db.put(
                        columnFamilyHandles.get(1), new WriteOptions(), "key".getBytes(),
                        "value".getBytes());

                // atomic write
                try (final WriteBatch wb = new WriteBatch()) {
                    wb.put(columnFamilyHandles.get(0), "key2".getBytes(),
                            "value2".getBytes());
                    wb.put(columnFamilyHandles.get(1), "key3".getBytes(),
                            "value3".getBytes());
                    wb.delete(columnFamilyHandles.get(1), "key".getBytes());
                    db.write(new WriteOptions(), wb);
                }

                // drop column family
                db.dropColumnFamily(columnFamilyHandles.get(1));
            } finally {
                for (final ColumnFamilyHandle handle : columnFamilyHandles) {
                    handle.close();
                }
            }
        }
    }
}
