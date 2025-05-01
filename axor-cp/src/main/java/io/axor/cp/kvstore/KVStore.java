package io.axor.cp.kvstore;

import com.typesafe.config.Config;
import io.axor.cp.kvstore.exception.StoreException;
import io.axor.cp.proto.KVStoreProto;

import java.io.Closeable;

public interface KVStore extends Closeable {
    long ROOT_ID = 0;
    KVStoreProto.Node ROOT_NODE = KVStoreProto.Node
            .newBuilder()
            .setId(ROOT_ID)
            .setName("/")
            .build();

    static KVStore create(Config config) throws StoreException {
        return new KVStoreImpl(config);
    }

    KVStoreProto.Node get(KVStoreProto.GetById req) throws StoreException;

    KVStoreProto.Node get(KVStoreProto.GetByPath req) throws StoreException;

    KVStoreProto.Node getParent(KVStoreProto.GetParentById req) throws StoreException;

    KVStoreProto.Node getParent(KVStoreProto.GetParentByPath req) throws StoreException;

    KVStoreProto.Node getChild(KVStoreProto.GetChildById req) throws StoreException;

    KVStoreProto.Node getChild(KVStoreProto.GetChildByPath req) throws StoreException;

    KVStoreProto.Nodes list(KVStoreProto.ListChildrenById req) throws StoreException;

    KVStoreProto.Nodes list(KVStoreProto.ListChildrenByPath req) throws StoreException;

    KVStoreProto.NodeHeader create(long id, KVStoreProto.CreateById req) throws StoreException;

    KVStoreProto.NodeHeader create(long id, KVStoreProto.CreateByPath req) throws StoreException;

    void update(KVStoreProto.UpdateById req) throws StoreException;

    void update(KVStoreProto.UpdateByPath req) throws StoreException;

    void delete(KVStoreProto.DeleteById req) throws StoreException;

    void delete(KVStoreProto.DeleteByPath req) throws StoreException;

    @Override
    void close() throws StoreException;
}
