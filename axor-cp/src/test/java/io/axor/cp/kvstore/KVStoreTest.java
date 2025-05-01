package io.axor.cp.kvstore;

import com.google.protobuf.ByteString;
import com.typesafe.config.ConfigFactory;
import io.axor.cp.kvstore.exception.NodeNotFoundException;
import io.axor.cp.kvstore.exception.OperationNotAllowedException;
import io.axor.cp.proto.KVStoreProto;
import org.apache.ratis.util.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.List;

public class KVStoreTest {
    static KVStore store;

    @BeforeClass
    public static void setup() throws Exception {
        FileUtils.createDirectories(new File(".tmp"));
        store = KVStore.create(ConfigFactory.parseString("""
                path=.tmp/kvstore
                maxValueLen=64k
                dbOptions.create_if_missing = true
                """));
        for (KVStoreProto.Node node : store.list(KVStoreProto.ListChildrenById.newBuilder()
                .setId(KVStore.ROOT_ID)
                .setIgnoreData(true)
                .build()).getNodesList()) {
            store.delete(KVStoreProto.DeleteById.newBuilder()
                    .setId(node.getId())
                    .setRecursive(true)
                    .build());
        }
    }

    @AfterClass
    public static void cleanup() throws Exception {
        if (store != null) {
            store.close();
        }
    }

    @Test
    public void getChild() throws Exception {
        KVStoreProto.GetChildById req1 = KVStoreProto.GetChildById.newBuilder()
                .setId(KVStore.ROOT_ID)
                .setChildName("test_get_child")
                .build();
        KVStoreProto.GetChildById req2 = KVStoreProto.GetChildById.newBuilder()
                .setId(1)
                .setChildName("test_get_child2")
                .build();
        Assert.assertThrows(NodeNotFoundException.class, () -> store.getChild(req1));
        Assert.assertThrows(NodeNotFoundException.class, () -> store.getChild(req2));
        KVStoreProto.CreateById req = KVStoreProto.CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName("test_get_child")
                .setData(ByteString.copyFromUtf8("data"))
                .build();
        store.create(1, req);
        KVStoreProto.Node node = store.getChild(req1);
        assertNodeEqual(1, KVStore.ROOT_ID, "test_get_child", "data", node);
        Assert.assertThrows(NodeNotFoundException.class, () -> store.getChild(req2));

        req = KVStoreProto.CreateById.newBuilder()
                .setParentId(1)
                .setName("test_get_child2")
                .setData(ByteString.copyFromUtf8("data2"))
                .build();
        store.create(2, req);
        node = store.getChild(req2);
        assertNodeEqual(2, 1, "test_get_child2", "data2", node);
    }

    @Test
    public void list() throws Exception {
        var listReq = KVStoreProto.ListChildrenById.newBuilder().setId(3).build();
        Assert.assertThrows(NodeNotFoundException.class, () -> store.list(listReq));
        KVStoreProto.CreateById req = KVStoreProto.CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName("test_list_parent")
                .setData(ByteString.copyFromUtf8("data"))
                .build();
        store.create(3, req);
        List<KVStoreProto.Node> list = store.list(listReq).getNodesList();
        Assert.assertEquals(0, list.size());

        req = KVStoreProto.CreateById.newBuilder()
                .setParentId(3)
                .setName("test_list1")
                .setData(ByteString.copyFromUtf8("data2"))
                .build();
        store.create(4, req);
        list = store.list(listReq).getNodesList();
        Assert.assertEquals(1, list.size());
        assertNodeEqual(4, 3, "test_list1", "data2", list.getFirst());

        req = KVStoreProto.CreateById.newBuilder()
                .setParentId(3)
                .setName("test_list2")
                .setData(ByteString.copyFromUtf8("data3"))
                .build();
        store.create(5, req);
        list = store.list(listReq).getNodesList();
        Assert.assertEquals(2, list.size());
        assertNodeEqual(4, 3, "test_list1", "data2", list.getFirst());
        assertNodeEqual(5, 3, "test_list2", "data3", list.get(1));

        list = store.list(listReq.toBuilder().setIgnoreData(true).build()).getNodesList();
        Assert.assertEquals(2, list.size());
        assertNodeEqual(4, 3, "test_list1", "", list.getFirst());
        assertNodeEqual(5, 3, "test_list2", "", list.get(1));
    }

    private void assertNodeEqual(long id, long parent, String name, String data) throws Exception {
        assertNodeEqual(id, parent, name, data, false);
    }

    private void assertNodeEqual(long id, long parent, String name, String data,
                                 boolean ignoreData) throws Exception {
        KVStoreProto.Node actual = store.get(KVStoreProto.GetById.newBuilder()
                .setId(id)
                .setIgnoreData(ignoreData)
                .build());
        assertNodeEqual(id, parent, name, data, actual);
    }

    private void assertNodeEqual(long id, long parent, String name, String data,
                                 KVStoreProto.Node actual) throws Exception {
        Assert.assertEquals(id, actual.getId());
        Assert.assertEquals(parent, actual.getParentId());
        Assert.assertEquals(name, actual.getName());
        Assert.assertTrue(actual.getCreateTime() > 0);
        Assert.assertEquals(ByteString.copyFromUtf8(data), actual.getData());
    }

    @Test
    public void create() throws Exception {
        KVStoreProto.CreateById req = KVStoreProto.CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName("test_create")
                .setData(ByteString.copyFromUtf8("data"))
                .build();
        store.create(6, req);
        assertNodeEqual(6, KVStore.ROOT_ID, "test_create", "data");

        req = KVStoreProto.CreateById.newBuilder()
                .setParentId(6)
                .setName("test_create2")
                .setData(ByteString.copyFromUtf8("data2"))
                .build();
        store.create(7, req);
        assertNodeEqual(7, 6, "test_create2", "data2");
        assertNodeEqual(7, 6, "test_create2", "", true);
    }

    @Test
    public void update() throws Exception {
        KVStoreProto.CreateById req = KVStoreProto.CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName("test_update")
                .setData(ByteString.copyFromUtf8("data"))
                .build();
        store.create(8, req);
        assertNodeEqual(8, KVStore.ROOT_ID, "test_update", "data");

        store.update(KVStoreProto.UpdateById.newBuilder()
                .setId(8)
                .setData(ByteString.copyFromUtf8("data2"))
                .build());
        assertNodeEqual(8, KVStore.ROOT_ID, "test_update", "data2");
    }

    @Test
    public void delete() throws Exception {
        KVStoreProto.CreateById req = KVStoreProto.CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName("test_delete")
                .setData(ByteString.copyFromUtf8("data"))
                .build();
        store.create(9, req);
        store.delete(KVStoreProto.DeleteById.newBuilder()
                .setId(9)
                .build());
        Assert.assertThrows(NodeNotFoundException.class, () -> {
            KVStoreProto.Node node = store.get(KVStoreProto.GetById.newBuilder()
                    .setId(9)
                    .build());
            System.out.println(node);
        });

        store.create(9, req);
        assertNodeEqual(9, KVStore.ROOT_ID, "test_delete", "data");
        req = KVStoreProto.CreateById.newBuilder()
                .setParentId(9)
                .setName("test_delete2")
                .setData(ByteString.copyFromUtf8("data2"))
                .build();
        store.create(10, req);
        req = KVStoreProto.CreateById.newBuilder()
                .setParentId(10)
                .setName("test_delete2")
                .setData(ByteString.copyFromUtf8("data2"))
                .build();
        store.create(11, req);
        Assert.assertThrows(OperationNotAllowedException.class,
                () -> store.delete(KVStoreProto.DeleteById.newBuilder()
                        .setId(9)
                        .build()));
        store.delete(KVStoreProto.DeleteById.newBuilder()
                .setId(9)
                .setRecursive(true)
                .build());

        req = KVStoreProto.CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName("test_delete")
                .setData(ByteString.copyFromUtf8("data"))
                .build();
        store.create(9, req);
        req = KVStoreProto.CreateById.newBuilder()
                .setParentId(9)
                .setName("test_delete2")
                .setData(ByteString.copyFromUtf8("data2"))
                .build();
        store.create(10, req);
        req = KVStoreProto.CreateById.newBuilder()
                .setParentId(10)
                .setName("test_delete2")
                .setData(ByteString.copyFromUtf8("data2"))
                .build();
        store.create(11, req);
        assertNodeEqual(9, KVStore.ROOT_ID, "test_delete", "data");
        assertNodeEqual(10, 9, "test_delete2", "data2");
        assertNodeEqual(11, 10, "test_delete2", "data2");

        Assert.assertThrows(OperationNotAllowedException.class,
                () -> store.delete(KVStoreProto.DeleteById.newBuilder()
                        .setId(KVStore.ROOT_ID)
                        .setRecursive(true)
                        .build()));

        Assert.assertThrows(OperationNotAllowedException.class,
                () -> store.delete(KVStoreProto.DeleteById.newBuilder()
                        .setId(9)
                        .setRecursive(false)
                        .build()));
    }

    @Test
    public void getParent() throws Exception {
        KVStoreProto.CreateById req = KVStoreProto.CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName("test_get_parent")
                .setData(ByteString.copyFromUtf8("data"))
                .build();
        store.create(12, req);
        req = KVStoreProto.CreateById.newBuilder()
                .setParentId(12)
                .setName("test_get_parent2")
                .setData(ByteString.copyFromUtf8("data2"))
                .build();
        store.create(13, req);
        Assert.assertEquals(KVStore.ROOT_NODE,
                store.getParent(KVStoreProto.GetParentById.newBuilder()
                        .setId(12)
                        .build()));
        assertNodeEqual(12, KVStore.ROOT_ID, "test_get_parent", "data",
                store.getParent(KVStoreProto.GetParentById.newBuilder()
                        .setId(13)
                        .build()));
    }

    @Test
    public void seek() throws Exception {
        KVStoreProto.CreateById req = KVStoreProto.CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName("test_seek")
                .setData(ByteString.copyFromUtf8("data"))
                .build();
        store.create(14, req);
        req = KVStoreProto.CreateById.newBuilder()
                .setParentId(14)
                .setName("test_seek2")
                .setData(ByteString.copyFromUtf8("data2"))
                .build();
        store.create(15, req);
        Assert.assertEquals(KVStore.ROOT_ID,
                ((KVStoreImpl) store).seekForId(KVStoreProto.NodePath.newBuilder()
                        .build()));
        Assert.assertEquals(14, ((KVStoreImpl) store).seekForId(KVStoreProto.NodePath.newBuilder()
                .addElem("test_seek")
                .build()));
        Assert.assertEquals(15, ((KVStoreImpl) store).seekForId(KVStoreProto.NodePath.newBuilder()
                .addElem("test_seek")
                .addElem("test_seek2")
                .build()));
        Assert.assertThrows(NodeNotFoundException.class, () -> {
            ((KVStoreImpl) store).seekForId(KVStoreProto.NodePath.newBuilder()
                    .addElem("test_seek")
                    .addElem("test_seek3")
                    .build());
        });
    }
}
