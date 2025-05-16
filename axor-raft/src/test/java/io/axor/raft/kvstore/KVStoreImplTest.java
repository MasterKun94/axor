package io.axor.raft.kvstore;

import com.google.protobuf.ByteString;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.axor.raft.kvstore.exception.NodeAlreadyExistsException;
import io.axor.raft.kvstore.exception.NodeNotFoundException;
import io.axor.raft.kvstore.exception.OperationNotAllowedException;
import io.axor.raft.kvstore.exception.ParentNotFoundException;
import io.axor.raft.kvstore.exception.StoreException;
import io.axor.raft.proto.KVStoreProto.CreateById;
import io.axor.raft.proto.KVStoreProto.DeleteById;
import io.axor.raft.proto.KVStoreProto.GetById;
import io.axor.raft.proto.KVStoreProto.GetChildById;
import io.axor.raft.proto.KVStoreProto.GetParentById;
import io.axor.raft.proto.KVStoreProto.ListChildrenById;
import io.axor.raft.proto.KVStoreProto.Node;
import io.axor.raft.proto.KVStoreProto.NodeHeader;
import io.axor.raft.proto.KVStoreProto.Nodes;
import io.axor.raft.proto.KVStoreProto.UpdateById;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class KVStoreImplTest {

    private static final String TEST_DB_PATH = ".tmp/kvstore-test";
    private KVStore kvStore;
    private Config config;

    @Before
    public void setUp() throws Exception {
        // Clean up any previous test data
        FileUtils.deleteDirectory(new File(TEST_DB_PATH));

        // Create config for KVStoreImpl
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("path", TEST_DB_PATH);
        configMap.put("maxValueLen", "1m");

        // Add RocksDB options
        Map<String, Object> dbOptionsMap = new HashMap<>();
        dbOptionsMap.put("create_if_missing", "true");
        configMap.put("dbOptions", dbOptionsMap);

        config = ConfigFactory.parseMap(configMap);

        // Create KVStore instance
        kvStore = KVStore.create(config);
    }

    @After
    public void tearDown() throws Exception {
        // Close the KVStore
        if (kvStore != null) {
            kvStore.close();
        }

        // Clean up test data
        try {
            FileUtils.deleteDirectory(new File(TEST_DB_PATH));
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    @Test
    public void testGetRootNode() throws StoreException {
        // Test getting the root node
        Node rootNode = kvStore.get(GetById.newBuilder().setId(KVStore.ROOT_ID).build());

        assertEquals(KVStore.ROOT_ID, rootNode.getId());
        assertEquals("/", rootNode.getName());
    }

    @Test(expected = NodeNotFoundException.class)
    public void testGetNonExistentNode() throws StoreException {
        // Test getting a non-existent node
        kvStore.get(GetById.newBuilder().setId(1).build());
    }

    @Test
    public void testCreateAndGetNode() throws StoreException {
        // Create a node
        long nodeId = 1;
        String nodeName = "testNode";
        ByteString nodeData = ByteString.copyFromUtf8("test data");

        NodeHeader header = kvStore.create(nodeId, CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName(nodeName)
                .setData(nodeData)
                .build());

        assertEquals(nodeId, header.getId());
        assertEquals(nodeName, header.getName());

        // Get the node and verify its properties
        Node node = kvStore.get(GetById.newBuilder().setId(nodeId).build());

        assertEquals(nodeId, node.getId());
        assertEquals(nodeName, node.getName());
        assertEquals(KVStore.ROOT_ID, node.getParentId());
        assertEquals(nodeData, node.getData());
    }

    @Test
    public void testCreateAndGetNodeWithoutData() throws StoreException {
        // Create a node without data
        long nodeId = 2;
        String nodeName = "testNodeNoData";

        NodeHeader header = kvStore.create(nodeId, CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName(nodeName)
                .build());

        assertEquals(nodeId, header.getId());
        assertEquals(nodeName, header.getName());

        // Get the node and verify its properties
        Node node = kvStore.get(GetById.newBuilder().setId(nodeId).build());

        assertEquals(nodeId, node.getId());
        assertEquals(nodeName, node.getName());
        assertEquals(KVStore.ROOT_ID, node.getParentId());
        assertEquals(ByteString.EMPTY, node.getData());
    }

    @Test
    public void testGetParent() throws StoreException {
        // Create a node
        long nodeId = 3;
        String nodeName = "testParentNode";

        kvStore.create(nodeId, CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName(nodeName)
                .build());

        // Get the parent node
        Node parentNode = kvStore.getParent(GetParentById.newBuilder().setId(nodeId).build());

        assertEquals(KVStore.ROOT_ID, parentNode.getId());
        assertEquals("/", parentNode.getName());
    }

    @Test
    public void testCreateAndGetChild() throws StoreException {
        // Create a parent node
        long parentId = 4;
        String parentName = "parent";

        kvStore.create(parentId, CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName(parentName)
                .build());

        // Create a child node
        long childId = 5;
        String childName = "child";
        ByteString childData = ByteString.copyFromUtf8("child data");

        kvStore.create(childId, CreateById.newBuilder()
                .setParentId(parentId)
                .setName(childName)
                .setData(childData)
                .build());

        // Get the child node using getChild
        Node childNode = kvStore.getChild(GetChildById.newBuilder()
                .setId(parentId)
                .setChildName(childName)
                .build());

        assertEquals(childId, childNode.getId());
        assertEquals(childName, childNode.getName());
        assertEquals(parentId, childNode.getParentId());
        assertEquals(childData, childNode.getData());
    }

    @Test(expected = NodeNotFoundException.class)
    public void testGetChildWithNonExistentParent() throws StoreException {
        // Try to get a child of a non-existent parent
        kvStore.getChild(GetChildById.newBuilder()
                .setId(999)
                .setChildName("nonExistentChild")
                .build());
    }

    @Test(expected = NodeNotFoundException.class)
    public void testGetNonExistentChild() throws StoreException {
        // Create a parent node
        long parentId = 6;
        String parentName = "parentForNonExistentChild";

        kvStore.create(parentId, CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName(parentName)
                .build());

        // Try to get a non-existent child
        kvStore.getChild(GetChildById.newBuilder()
                .setId(parentId)
                .setChildName("nonExistentChild")
                .build());
    }

    @Test
    public void testListChildren() throws StoreException {
        // Create a parent node
        long parentId = 7;
        String parentName = "parentForList";

        kvStore.create(parentId, CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName(parentName)
                .build());

        // Create child nodes
        long childId1 = 8;
        String childName1 = "child1";

        long childId2 = 9;
        String childName2 = "child2";

        kvStore.create(childId1, CreateById.newBuilder()
                .setParentId(parentId)
                .setName(childName1)
                .build());

        kvStore.create(childId2, CreateById.newBuilder()
                .setParentId(parentId)
                .setName(childName2)
                .build());

        // List children
        Nodes children = kvStore.list(ListChildrenById.newBuilder()
                .setId(parentId)
                .build());

        assertEquals(2, children.getNodesCount());

        // Verify child nodes
        boolean foundChild1 = false;
        boolean foundChild2 = false;

        for (Node node : children.getNodesList()) {
            if (node.getId() == childId1) {
                assertEquals(childName1, node.getName());
                assertEquals(parentId, node.getParentId());
                foundChild1 = true;
            } else if (node.getId() == childId2) {
                assertEquals(childName2, node.getName());
                assertEquals(parentId, node.getParentId());
                foundChild2 = true;
            }
        }

        assertTrue("Child 1 not found in list", foundChild1);
        assertTrue("Child 2 not found in list", foundChild2);
    }

    @Test
    public void testListEmptyChildren() throws StoreException {
        // Create a parent node with no children
        long parentId = 10;
        String parentName = "emptyParent";

        kvStore.create(parentId, CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName(parentName)
                .build());

        // List children
        Nodes children = kvStore.list(ListChildrenById.newBuilder()
                .setId(parentId)
                .build());

        assertEquals(0, children.getNodesCount());
    }

    @Test(expected = NodeNotFoundException.class)
    public void testListChildrenOfNonExistentNode() throws StoreException {
        // Try to list children of a non-existent node
        kvStore.list(ListChildrenById.newBuilder()
                .setId(999)
                .build());
    }

    @Test
    public void testUpdateNode() throws StoreException {
        // Create a node
        long nodeId = 11;
        String nodeName = "updateNode";
        ByteString initialData = ByteString.copyFromUtf8("initial data");

        kvStore.create(nodeId, CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName(nodeName)
                .setData(initialData)
                .build());

        // Update the node
        ByteString updatedData = ByteString.copyFromUtf8("updated data");
        kvStore.update(UpdateById.newBuilder()
                .setId(nodeId)
                .setData(updatedData)
                .build());

        // Get the node and verify the data was updated
        Node node = kvStore.get(GetById.newBuilder().setId(nodeId).build());

        assertEquals(nodeId, node.getId());
        assertEquals(nodeName, node.getName());
        assertEquals(updatedData, node.getData());
    }

    @Test(expected = NodeNotFoundException.class)
    public void testUpdateNonExistentNode() throws StoreException {
        // Try to update a non-existent node
        kvStore.update(UpdateById.newBuilder()
                .setId(999)
                .setData(ByteString.copyFromUtf8("data"))
                .build());
    }

    @Test(expected = OperationNotAllowedException.class)
    public void testUpdateRootNode() throws StoreException {
        // Try to update the root node
        kvStore.update(UpdateById.newBuilder()
                .setId(KVStore.ROOT_ID)
                .setData(ByteString.copyFromUtf8("data"))
                .build());
    }

    @Test
    public void testDeleteNode() throws StoreException {
        // Create a node
        long nodeId = 12;
        String nodeName = "deleteNode";

        kvStore.create(nodeId, CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName(nodeName)
                .build());

        // Delete the node
        kvStore.delete(DeleteById.newBuilder()
                .setId(nodeId)
                .setRecursive(false)
                .build());

        // Verify the node is deleted
        try {
            kvStore.get(GetById.newBuilder().setId(nodeId).build());
            fail("Expected NodeNotFoundException");
        } catch (NodeNotFoundException e) {
            // Expected
        }
    }

    @Test(expected = NodeNotFoundException.class)
    public void testDeleteNonExistentNode() throws StoreException {
        // Try to delete a non-existent node
        kvStore.delete(DeleteById.newBuilder()
                .setId(999)
                .setRecursive(false)
                .build());
    }

    @Test(expected = OperationNotAllowedException.class)
    public void testDeleteRootNode() throws StoreException {
        // Try to delete the root node
        kvStore.delete(DeleteById.newBuilder()
                .setId(KVStore.ROOT_ID)
                .setRecursive(false)
                .build());
    }

    @Test(expected = OperationNotAllowedException.class)
    public void testDeleteNodeWithChildrenNonRecursive() throws StoreException {
        // Create a parent node
        long parentId = 13;
        String parentName = "parentForDelete";

        kvStore.create(parentId, CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName(parentName)
                .build());

        // Create a child node
        long childId = 14;
        String childName = "childForDelete";

        kvStore.create(childId, CreateById.newBuilder()
                .setParentId(parentId)
                .setName(childName)
                .build());

        // Try to delete the parent node non-recursively
        kvStore.delete(DeleteById.newBuilder()
                .setId(parentId)
                .setRecursive(false)
                .build());
    }

    @Test
    public void testDeleteNodeWithChildrenRecursive() throws StoreException {
        // Create a parent node
        long parentId = 15;
        String parentName = "parentForRecursiveDelete";

        kvStore.create(parentId, CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName(parentName)
                .build());

        // Create a child node
        long childId = 16;
        String childName = "childForRecursiveDelete";

        kvStore.create(childId, CreateById.newBuilder()
                .setParentId(parentId)
                .setName(childName)
                .build());

        // Delete the parent node recursively
        kvStore.delete(DeleteById.newBuilder()
                .setId(parentId)
                .setRecursive(true)
                .build());

        // Verify both nodes are deleted
        try {
            kvStore.get(GetById.newBuilder().setId(parentId).build());
            fail("Expected NodeNotFoundException for parent");
        } catch (NodeNotFoundException e) {
            // Expected
        }

        try {
            kvStore.get(GetById.newBuilder().setId(childId).build());
            fail("Expected NodeNotFoundException for child");
        } catch (NodeNotFoundException e) {
            // Expected
        }
    }

    @Test(expected = NodeAlreadyExistsException.class)
    public void testCreateDuplicateNode() throws StoreException {
        // Create a node
        long nodeId1 = 17;
        long nodeId2 = 18;
        String nodeName = "duplicateName";

        kvStore.create(nodeId1, CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName(nodeName)
                .build());

        // Try to create another node with the same name under the same parent
        kvStore.create(nodeId2, CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName(nodeName)
                .build());
    }

    @Test(expected = ParentNotFoundException.class)
    public void testCreateNodeWithNonExistentParent() throws StoreException {
        // Try to create a node with a non-existent parent
        kvStore.create(19, CreateById.newBuilder()
                .setParentId(999)
                .setName("orphanNode")
                .build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateNodeWithEmptyName() throws StoreException {
        // Try to create a node with an empty name
        kvStore.create(20, CreateById.newBuilder()
                .setParentId(KVStore.ROOT_ID)
                .setName("")
                .build());
    }
}
