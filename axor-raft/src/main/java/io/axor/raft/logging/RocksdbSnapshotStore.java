package io.axor.raft.logging;

import com.google.protobuf.InvalidProtocolBufferException;
import io.axor.commons.util.ByteArray;
import io.axor.raft.RaftException;
import io.axor.raft.RocksDBRaftException;
import io.axor.raft.proto.PeerProto;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RocksdbSnapshotStore implements SnapshotStore {
    private final OptimisticTransactionDB db;
    private final ColumnFamilyHandle cfHandle;
    private final ReadOptions readOpt;
    private final WriteOptions writeOpt;

    public RocksdbSnapshotStore(OptimisticTransactionDB db,
                                ColumnFamilyHandle cfHandle,
                                ReadOptions readOpt,
                                WriteOptions writeOpt) {
        this.db = db;
        this.cfHandle = cfHandle;
        this.readOpt = readOpt;
        this.writeOpt = writeOpt;
    }

    private PeerProto.SnapshotResult readSnapshotResult(byte[] bytes) throws RaftException {
        try {
            return PeerProto.SnapshotResult.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RaftException("Failed to parse snapshot result", e);
        }
    }

    private long readId(byte[] bytes) {
        return ByteArray.getLong(bytes, 0);
    }

    @Override
    public PeerProto.SnapshotResult getLatest() throws RaftException {
        try (var iter = db.newIterator(cfHandle, readOpt)) {
            iter.seekToLast();
            if (iter.isValid()) {
                return readSnapshotResult(iter.value());
            }
            return PeerProto.SnapshotResult.newBuilder()
                    .setSnapshot(SnapshotStore.INITIAL_SNAPSHOT)
                    .setSuccess(true)
                    .build();
        }
    }

    @Override
    public PeerProto.Snapshot getLatestSucceed() throws RaftException {
        try (var iter = db.newIterator(cfHandle, readOpt)) {
            iter.seekToLast();
            while (iter.isValid()) {
                PeerProto.SnapshotResult result = readSnapshotResult(iter.value());
                if (result.getSuccess()) {
                    return result.getSnapshot();
                }
                iter.prev();
            }
            return SnapshotStore.INITIAL_SNAPSHOT;
        }
    }

    @Override
    public ClosableIterator<PeerProto.SnapshotResult> list() throws RaftException {
        var iter = db.newIterator(cfHandle, readOpt);
        return new ClosableIterator<>() {
            @Override
            public void close() throws IOException {
                iter.close();
            }

            @Override
            public boolean hasNext() {
                return iter.isValid();
            }

            @Override
            public PeerProto.SnapshotResult next() {
                try {
                    PeerProto.SnapshotResult result = readSnapshotResult(iter.value());
                    iter.next();
                    return result;
                } catch (RaftException e) {
                    throw new RuntimeException("Failed to read snapshot result", e);
                }
            }
        };
    }

    @Override
    public List<Long> listId() {
        try (var iter = db.newIterator(cfHandle, readOpt)) {
            iter.seekToFirst();
            List<Long> list = new ArrayList<>();
            while (iter.isValid()) {
                list.add(readId(iter.key()));
                iter.next();
            }
            return list;
        }
    }

    @Override
    public void save(PeerProto.SnapshotResult result) throws RaftException {
        long expectSnapshotId;
        try (var iter = db.newIterator(cfHandle, readOpt)) {
            iter.seekToLast();
            if (iter.isValid()) {
                expectSnapshotId = readId(iter.key()) + 1;
            } else {
                expectSnapshotId = 1;
            }
        }
        if (expectSnapshotId != result.getSnapshot().getId()) {
            throw new IllegalArgumentException(String.format("unexpected snapshot id %d, but got " +
                                                             "%d",
                    expectSnapshotId,
                    result.getSnapshot().getId()));
        }
        byte[] key = new byte[8];
        ByteArray.setLong(key, 0, result.getSnapshot().getId());
        try {
            db.put(cfHandle, writeOpt, key, result.toByteArray());
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
    }

    @Override
    public void install(PeerProto.Snapshot snapshot) throws RaftException {
        List<Long> ids = listId();
        try (Transaction txn = db.beginTransaction(writeOpt)) {
            byte[] key = new byte[8];
            for (long id : ids) {
                ByteArray.setLong(key, 0, id);
                txn.singleDelete(cfHandle, key);
            }
            ByteArray.setLong(key, 0, snapshot.getId());
            txn.put(cfHandle, key, PeerProto.SnapshotResult.newBuilder()
                    .setSnapshot(snapshot)
                    .setSuccess(true)
                    .build().toByteArray());
            txn.commit();
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
    }

    @Override
    public PeerProto.SnapshotResult read(long id) throws RaftException {
        byte[] key = new byte[8];
        ByteArray.setLong(key, 0, id);
        try {
            byte[] bytes = db.get(cfHandle, readOpt, key);
            if (bytes == null) {
                return null;
            }
            return PeerProto.SnapshotResult.parseFrom(bytes);
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        } catch (InvalidProtocolBufferException e) {
            throw new RaftException("Failed to parse snapshot result", e);
        }
    }

    @Override
    public void delete(List<Long> ids) throws RaftException {
        try (Transaction txn = db.beginTransaction(writeOpt)) {
            byte[] key = new byte[8];
            for (long id : ids) {
                ByteArray.setLong(key, 0, id);
                txn.singleDelete(cfHandle, key);
            }
            txn.commit();
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
    }
}
