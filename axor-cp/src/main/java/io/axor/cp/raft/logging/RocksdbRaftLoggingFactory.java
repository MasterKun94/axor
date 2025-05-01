package io.axor.cp.raft.logging;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigMemorySize;
import io.axor.cp.raft.RaftException;
import io.axor.cp.raft.RocksDBRaftException;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class RocksdbRaftLoggingFactory implements RaftLoggingFactory {

    static {
        RocksDB.loadLibrary();
    }

    private final ThreadLocal<ByteBuffer> bufferTl;
    private final Config config;
    private final Map<String, ColumnFamilyHandle> cfHandles = new ConcurrentHashMap<>();
    private final Set<String> created = new ConcurrentSkipListSet<>();
    private volatile ColumnFamilyOptions cfOpt;
    private volatile boolean closed;
    private volatile OptimisticTransactionDB db;

    public RocksdbRaftLoggingFactory(Config config) {
        this.config = config;
        boolean direct = config.getBoolean("bufferDirect");
        ConfigMemorySize bufferMax = config.getMemorySize("bufferMax");
        if (bufferMax.toBytes() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("illegal bufferMax: " + bufferMax);
        }
        int bufferMaxValue = (int) bufferMax.toBytes();
        if (direct) {
            bufferTl = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(bufferMaxValue));
        } else {
            bufferTl = ThreadLocal.withInitial(() -> ByteBuffer.allocate(bufferMaxValue));
        }
    }

    public void init() throws RaftException {
        if (closed) {
            throw new IllegalArgumentException("already closed");
        }
        if (db == null) {
            synchronized (this) {
                if (closed) {
                    throw new IllegalArgumentException("already closed");
                }
                if (db == null) {
                    String path = config.getString("path");
                    Properties dbOptions = new Properties();
                    if (config.hasPath("dbOptions")) {
                        for (var entry : config.getConfig("dbOptions").entrySet()) {
                            dbOptions.setProperty(entry.getKey(),
                                    entry.getValue().unwrapped().toString());
                        }
                    }
                    Properties cfOptions = new Properties();
                    if (config.hasPath("columnFamilyOptions")) {
                        for (var entry : config.getConfig("columnFamilyOptions").entrySet()) {
                            cfOptions.setProperty(entry.getKey(),
                                    entry.getValue().unwrapped().toString());
                        }
                    }
                    DBOptions dbOpt = dbOptions.isEmpty() ? new DBOptions() :
                            DBOptions.getDBOptionsFromProps(dbOptions);
                    this.cfOpt = cfOptions.isEmpty() ? new ColumnFamilyOptions() :
                            ColumnFamilyOptions.getColumnFamilyOptionsFromProps(cfOptions);
                    Options options = new Options(dbOpt, cfOpt);
                    try {
                        if (!new File(path).exists()) {
                            options.setCreateIfMissing(true);
                            db = OptimisticTransactionDB.open(options, path);
                            return;
                        }
                        List<ColumnFamilyDescriptor> cfDesc =
                                OptimisticTransactionDB.listColumnFamilies(options, path)
                                        .stream()
                                        .map(b -> new ColumnFamilyDescriptor(b, cfOpt))
                                        .toList();
                        List<ColumnFamilyHandle> handles = new ArrayList<>();
                        db = OptimisticTransactionDB.open(dbOpt, path, cfDesc, handles);
                        for (int i = 0; i < cfDesc.size(); i++) {
                            ColumnFamilyDescriptor desc = cfDesc.get(i);
                            cfHandles.put(new String(desc.getName()), handles.get(i));
                        }
                    } catch (RocksDBException e) {
                        throw new RocksDBRaftException(e);
                    }
                }
            }
        }
    }

    @Override
    public RaftLogging create(String name) throws RaftException {
        init();
        if (!created.add(name)) {
            throw new IllegalArgumentException("already created");
        }
        try {
            ColumnFamilyHandle handle = cfHandles.get(name);
            if (handle == null) {
                handle = db.createColumnFamily(new ColumnFamilyDescriptor(name.getBytes(), cfOpt));
                ColumnFamilyHandle prev = cfHandles.put(name, handle);
                assert prev == null;
            }
            return new RocksdbRaftLogging(name, db, handle, new WriteOptions(), new ReadOptions(),
                    Thread.currentThread(), bufferTl);
        } catch (RocksDBException e) {
            created.remove(name);
            throw new RocksDBRaftException(e);
        } catch (Exception e) {
            created.remove(name);
            throw e;
        }
    }

    @Override
    public void close() {
        if (!closed) {
            synchronized (this) {
                closed = true;
                if (db != null) {
                    db.close();
                    db = null;
                }
            }
        }
    }
}
