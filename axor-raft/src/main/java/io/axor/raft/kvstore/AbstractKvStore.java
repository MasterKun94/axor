package io.axor.raft.kvstore;

import com.google.protobuf.ByteString;
import io.axor.raft.proto.KVStoreProto;
import io.axor.raft.proto.KVStoreProto.Node;
import io.axor.raft.proto.KVStoreProto.NodeHeader;
import io.axor.raft.proto.KVStoreProto.Nodes;
import io.axor.raft.kvstore.exception.StoreException;
import org.rocksdb.RocksDBException;

public abstract class AbstractKvStore implements KVStore {

    protected long seekForId(KVStoreProto.NodePath path) throws RocksDBException, StoreException {
        long node = ROOT_ID;
        for (String pathElem : path.getElemList()) {
            node = getChildId(node, pathElem);
        }
        return node;
    }

    abstract Node get(long id, boolean ignoreData) throws RocksDBException, StoreException;

    abstract Node getParent(long id, boolean ignoreData) throws RocksDBException, StoreException;

    abstract long getChildId(long id, String name) throws RocksDBException, StoreException;

    abstract Node getChild(long id, String name,
                           boolean ignoreData) throws RocksDBException, StoreException;

    abstract Nodes list(long id, boolean ignoreData) throws RocksDBException, StoreException;

    abstract NodeHeader create(long id, long parentId, String name,
                               ByteString data) throws RocksDBException, StoreException;

    abstract void update(long id, ByteString data) throws RocksDBException, StoreException;

    abstract void delete(long id, boolean recursive) throws RocksDBException, StoreException;

    @Override
    public Node get(KVStoreProto.GetById req) throws StoreException {
        try {
            return get(req.getId(), req.getIgnoreData());
        } catch (RocksDBException e) {
            throw StoreUtil.convert(e);
        }
    }

    @Override
    public Node get(KVStoreProto.GetByPath req) throws StoreException {
        try {
            return get(seekForId(req.getPath()), req.getIgnoreData());
        } catch (RocksDBException e) {
            throw StoreUtil.convert(e);
        }
    }

    @Override
    public Node getParent(KVStoreProto.GetParentById req) throws StoreException {
        try {
            return getParent(req.getId(), req.getIgnoreData());
        } catch (RocksDBException e) {
            throw StoreUtil.convert(e);
        }
    }

    @Override
    public Node getParent(KVStoreProto.GetParentByPath req) throws StoreException {
        try {
            return getParent(seekForId(req.getPath()), req.getIgnoreData());
        } catch (RocksDBException e) {
            throw StoreUtil.convert(e);
        }
    }

    @Override
    public Node getChild(KVStoreProto.GetChildById req) throws StoreException {
        try {
            return getChild(req.getId(), req.getChildName(), req.getIgnoreData());
        } catch (RocksDBException e) {
            throw StoreUtil.convert(e);
        }
    }

    @Override
    public Node getChild(KVStoreProto.GetChildByPath req) throws StoreException {
        try {
            return getChild(seekForId(req.getPath()), req.getChildName(), req.getIgnoreData());
        } catch (RocksDBException e) {
            throw StoreUtil.convert(e);
        }
    }

    @Override
    public Nodes list(KVStoreProto.ListChildrenById req) throws StoreException {
        try {
            return list(req.getId(), req.getIgnoreData());
        } catch (RocksDBException e) {
            throw StoreUtil.convert(e);
        }
    }

    @Override
    public Nodes list(KVStoreProto.ListChildrenByPath req) throws StoreException {
        try {
            return list(seekForId(req.getPath()), req.getIgnoreData());
        } catch (RocksDBException e) {
            throw StoreUtil.convert(e);
        }
    }

    @Override
    public NodeHeader create(long id, KVStoreProto.CreateById req) throws StoreException {
        try {
            return create(id, req.getParentId(), req.getName(), req.getData());
        } catch (RocksDBException e) {
            throw StoreUtil.convert(e);
        }
    }

    @Override
    public NodeHeader create(long id, KVStoreProto.CreateByPath req) throws StoreException {
        try {
            return create(id, seekForId(req.getPath()), req.getName(), req.getData());
        } catch (RocksDBException e) {
            throw StoreUtil.convert(e);
        }
    }

    @Override
    public void update(KVStoreProto.UpdateById req) throws StoreException {
        try {
            update(req.getId(), req.getData());
        } catch (RocksDBException e) {
            throw StoreUtil.convert(e);
        }
    }

    @Override
    public void update(KVStoreProto.UpdateByPath req) throws StoreException {
        try {
            update(seekForId(req.getPath()), req.getData());
        } catch (RocksDBException e) {
            throw StoreUtil.convert(e);
        }
    }

    @Override
    public void delete(KVStoreProto.DeleteById req) throws StoreException {
        try {
            delete(req.getId(), req.getRecursive());
        } catch (RocksDBException e) {
            throw StoreUtil.convert(e);
        }
    }

    @Override
    public void delete(KVStoreProto.DeleteByPath req) throws StoreException {
        try {
            delete(seekForId(req.getPath()), req.getRecursive());
        } catch (RocksDBException e) {
            throw StoreUtil.convert(e);
        }
    }
}
