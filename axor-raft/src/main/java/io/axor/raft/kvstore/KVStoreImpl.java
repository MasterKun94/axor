package io.axor.raft.kvstore;

import com.google.protobuf.ByteString;
import com.typesafe.config.Config;
import io.axor.raft.kvstore.exception.NodeAlreadyExistsException;
import io.axor.raft.kvstore.exception.NodeNotFoundException;
import io.axor.raft.kvstore.exception.OperationNotAllowedException;
import io.axor.raft.kvstore.exception.ParentNotFoundException;
import io.axor.raft.kvstore.exception.RocksDBStoreException;
import io.axor.raft.kvstore.exception.StoreException;
import io.axor.raft.proto.KVStoreProto.Node;
import io.axor.raft.proto.KVStoreProto.NodeHeader;
import io.axor.raft.proto.KVStoreProto.Nodes;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Status;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Properties;

public class KVStoreImpl extends AbstractKvStore {
    private static final Logger LOG = LoggerFactory.getLogger(KVStoreImpl.class);

    static {
        RocksDB.loadLibrary();
    }

    private final OptimisticTransactionDB db;
    private final ThreadLocal<ByteBuffer> bufferTl;
    private final ReadOptions readOpt = new ReadOptions();
    private final WriteOptions writeOpt = new WriteOptions();

    public KVStoreImpl(Config config) throws StoreException {
        Properties dbOptions = new Properties();
        if (config.hasPath("dbOptions")) {
            for (var entry : config.getConfig("dbOptions").entrySet()) {
                dbOptions.setProperty(entry.getKey(), entry.getValue().unwrapped().toString());
            }
        }
        Properties cfOptions = new Properties();
        if (config.hasPath("columnFamilyOptions")) {
            for (var entry : config.getConfig("columnFamilyOptions").entrySet()) {
                cfOptions.setProperty(entry.getKey(), entry.getValue().unwrapped().toString());
            }
        }
        Options options = new Options(dbOptions.isEmpty() ? new DBOptions() :
                DBOptions.getDBOptionsFromProps(dbOptions), cfOptions.isEmpty() ?
                new ColumnFamilyOptions() :
                ColumnFamilyOptions.getColumnFamilyOptionsFromProps(cfOptions));
        String path = config.getString("path");
        long maxValueLen = config.getMemorySize("maxValueLen").toBytes();
        if (Integer.MAX_VALUE < maxValueLen) {
            throw new IllegalArgumentException("illegal maxValueLen value: " + maxValueLen);
        }
        try {
            db = OptimisticTransactionDB.open(options, path);
        } catch (RocksDBException e) {
            throw StoreUtil.convert(e);
        }

        bufferTl = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect((int) maxValueLen + 9));
    }

    private ByteBuffer getReuseBuffer() {
        ByteBuffer buffer = bufferTl.get();
        buffer.clear();
        return buffer;
    }

    @Override
    protected Node get(long id, boolean ignoreData) throws StoreException {
        if (id == ROOT_ID) {
            return ROOT_NODE;
        }
        ByteBuffer buffer = getReuseBuffer();
        ByteBuffer keyPrefix = StoreKey.keyPrefix(id, buffer);
        try (RocksIterator iter = db.newIterator()) {
            Node.Builder builder = Node.newBuilder();
            boolean found = false;
            for (iter.seek(keyPrefix); iter.isValid(); iter.next()) {
                found = true;
                buffer.clear();
                iter.key(buffer);
                StoreKey storeKey = StoreKey.bytesToKey(buffer);
                if (storeKey.id() != id) {
                    break;
                }
                buffer.clear();
                switch (storeKey) {
                    case StoreKey.HeadKey(var ignore) -> {
                        iter.value(buffer);
                        builder.setParentId(buffer.getLong()).setCreateTime(buffer.getLong()).setNameBytes(ByteString.copyFrom(buffer));
                    }
                    case StoreKey.ChildKey(var ignore, var ignore2) -> {
                    }
                    case StoreKey.DataKey(var ignore) -> {
                        if (!ignoreData) {
                            iter.value(buffer);
                            builder.setData(ByteString.copyFrom(buffer));
                        }
                    }
                }
            }
            if (found) {
                return builder.setId(id).build();
            }
            throw new NodeNotFoundException("node id: " + id);
        }
    }

    @Override
    Node getParent(long id, boolean ignoreData) throws RocksDBException, StoreException {
        ByteBuffer buffer = getReuseBuffer();
        ByteBuffer bKey = StoreKey.keyToBytes(new StoreKey.HeadKey(id), buffer);
        int pos = buffer.position();
        db.get(readOpt, bKey, buffer);
        long parentId = buffer.getLong(pos);
        return get(parentId, ignoreData);
    }

    @Override
    long getChildId(long id, String name) throws RocksDBException, StoreException {
        StoreKey childKey = new StoreKey.ChildKey(id, ByteString.copyFromUtf8(name));
        ByteBuffer buffer = getReuseBuffer();
        ByteBuffer bKey = StoreKey.keyToBytes(childKey, buffer);
        int start = buffer.position();
        int i = db.get(readOpt, bKey, buffer);
        if (RocksDB.NOT_FOUND == i) {
            throw new NodeNotFoundException("parent node id: " + id + ", child node name: " + name);
        }
        return buffer.getLong(start);
    }

    @Override
    protected Node getChild(long id, String name, boolean ignoreData) throws RocksDBException,
            StoreException {
        long childId = getChildId(id, name);
        return get(childId, ignoreData);
    }

    @Override
    protected Nodes list(long id, boolean ignoreData) throws RocksDBException, StoreException {
        ByteBuffer buffer = getReuseBuffer();
        ByteBuffer keyPrefix = StoreKey.keyPrefix(id, buffer);
        RocksIterator iter = db.newIterator();
        Nodes.Builder builder = Nodes.newBuilder();
        boolean parentExists = id == ROOT_ID;
        for (iter.seek(keyPrefix); iter.isValid(); iter.next()) {
            buffer.clear();
            iter.key(buffer);
            StoreKey storeKey = StoreKey.bytesToKey(buffer);
            if (storeKey.id() != id) {
                break;
            }
            if (storeKey instanceof StoreKey.HeadKey) {
                parentExists = true;
            } else if (storeKey instanceof StoreKey.ChildKey) {
                buffer.clear();
                int start = buffer.position();
                iter.value(buffer);
                long childId = buffer.getLong(start);
                Node node = get(childId, ignoreData);
                builder.addNodes(node);
            } else {
                break;
            }
        }
        if (parentExists) {
            return builder.build();
        } else {
            throw new NodeNotFoundException("node id: " + id);
        }
    }

    @Override
    protected NodeHeader create(long id, long parentId, String name, ByteString data) throws RocksDBException, StoreException {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("empty name");
        }
        ByteBuffer buffer = getReuseBuffer();

        try (Transaction txn = db.beginTransaction(writeOpt)) {
            ByteBuffer bKey;
            if (parentId != ROOT_ID) {
                bKey = StoreKey.keyToBytes(new StoreKey.HeadKey(parentId), buffer);
                if (!db.keyExists(readOpt, bKey)) {
                    throw new ParentNotFoundException("node id: " + parentId);
                }
            }

            buffer.clear();
            StoreKey.ChildKey childKey = new StoreKey.ChildKey(parentId, name);
            bKey = StoreKey.keyToBytes(childKey, buffer);
            if (db.keyExists(readOpt, bKey)) {
                throw new NodeAlreadyExistsException("parent node id: " + id + ", child node " +
                                                     "name: " + name);
            }
            int pos = buffer.position();
            buffer.putLong(id);
            txn.put(bKey, buffer.slice(pos, 8));

            bKey = StoreKey.keyToBytes(new StoreKey.HeadKey(id), buffer);
            pos = buffer.position();
            buffer.putLong(parentId).putLong(System.currentTimeMillis());
            for (ByteBuffer b : childKey.childName().asReadOnlyByteBufferList()) {
                buffer.put(b);
            }
            txn.put(bKey, buffer.slice(pos, buffer.position() - pos));
            if (data != null) {
                bKey = StoreKey.keyToBytes(new StoreKey.DataKey(id), buffer);
                txn.put(bKey, byteStringToBuffer(data, buffer));
            }
            txn.commit();
        }
        return NodeHeader.newBuilder().setId(id).setName(name).build();
    }

    private ByteBuffer byteStringToBuffer(ByteString b, ByteBuffer buffer) {
        int pos = buffer.position();
        List<ByteBuffer> list = b.asReadOnlyByteBufferList();
        if (list.size() == 1) {
            ByteBuffer b0 = list.getFirst();
            if (b0.isDirect() == buffer.isDirect()) {
                return b0;
            }
        }
        for (ByteBuffer b0 : list) {
            buffer.put(b0);
        }
        return buffer.slice(pos, buffer.position() - pos);
    }

    private void checkNodeExists(long id, ByteBuffer buffer) throws StoreException {
        if (id == ROOT_ID) {
            return;
        }
        ByteBuffer bKey = StoreKey.keyToBytes(new StoreKey.HeadKey(id), buffer);
        if (!db.keyExists(readOpt, bKey)) {
            throw new NodeNotFoundException("node id: " + id);
        }
    }

    @Override
    void update(long id, ByteString data) throws RocksDBException, StoreException {
        if (id == ROOT_ID) {
            throw new OperationNotAllowedException("update root not supported");
        }
        ByteBuffer buffer = getReuseBuffer();
        checkNodeExists(id, buffer);
        try (Transaction txn = db.beginTransaction(writeOpt)) {
            var bKey = StoreKey.keyToBytes(new StoreKey.DataKey(id), buffer);
            txn.put(bKey, byteStringToBuffer(data, buffer));
            txn.commit();
        }
    }

    @Override
    void delete(long id, boolean recursive) throws RocksDBException, StoreException {
        if (id == ROOT_ID) {
            throw new OperationNotAllowedException("delete root not supported");
        }
        ByteBuffer buffer = getReuseBuffer();
        checkNodeExists(id, buffer);
        try (Transaction txn = db.beginTransaction(writeOpt)) {
            delete(txn, id, recursive, buffer);
            txn.commit();
        }
    }

    private void delete(Transaction txn, long id, boolean recursive, ByteBuffer buffer) throws RocksDBException, StoreException {
        buffer.clear();
        ByteBuffer bKey = StoreKey.keyToBytes(new StoreKey.HeadKey(id), buffer);
        ByteBuffer slice = buffer.slice(buffer.position(), buffer.remaining());
        var getStatus = txn.get(readOpt, bKey, slice);
        if (getStatus.status.getCode() != Status.Code.Ok) {
            throw new RocksDBStoreException(getStatus.status);
        }
        slice.flip();
        long parentId = slice.getLong();
        slice.getLong();
        ByteString name = ByteString.copyFrom(slice);
        buffer.clear();
        ByteBuffer keyPrefix = StoreKey.childKeyPrefix(id, buffer);
        RocksIterator iter = txn.getIterator();
        for (iter.seek(keyPrefix); iter.isValid(); iter.next()) {
            buffer.clear();
            iter.key(buffer);
            StoreKey storeKey = StoreKey.bytesToKey(buffer);
            if (storeKey.id() == id && storeKey instanceof StoreKey.ChildKey) {
                if (recursive) {
                    buffer.clear();
                    iter.value(buffer);
                    long childId = buffer.getLong();
                    delete(txn, childId, true, buffer);
                } else {
                    throw new OperationNotAllowedException("node has child");
                }
            }
        }
        txn.delete(new StoreKey.ChildKey(parentId, name).toByteArray());
        txn.delete(new StoreKey.HeadKey(id).toByteArray());
        txn.delete(new StoreKey.DataKey(id).toByteArray());
    }

    @Override
    public void close() throws StoreException {
        db.close();
    }
}
