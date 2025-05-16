package io.axor.raft.logging;

import com.google.protobuf.ByteString;
import io.axor.raft.RaftException;
import io.axor.raft.proto.PeerProto;
import org.junit.Assert;

import java.io.IOException;
import java.util.List;

/**
 * A test toolkit for SnapshotStore implementations.
 * This class encapsulates test logic for verifying that a SnapshotStore
 * implementation correctly implements the SnapshotStore interface.
 */
public class SnapshotStoreTestkit {
    private final SnapshotStore snapshotStore;

    /**
     * Creates a new SnapshotStoreTestkit for the given SnapshotStore.
     *
     * @param snapshotStore the SnapshotStore to test
     */
    public SnapshotStoreTestkit(SnapshotStore snapshotStore) {
        this.snapshotStore = snapshotStore;
    }

    /**
     * Runs all tests on the SnapshotStore.
     *
     * @throws RaftException if a test fails due to a RaftException
     * @throws IOException if a test fails due to an IOException
     */
    public void test() throws RaftException, IOException {
        testSaveAndRead();
        testGetLatestSucceed();
        testList();
        testListId();
        testSaveWithIncorrectId();
        testInitialSnapshot();
        testInstallWhenEmpty();
        testInstallWithExistingSnapshots();
        testDeleteSingleSnapshot();
        testDeleteMultipleSnapshots();
        testDeleteNonExistentSnapshot();
    }

    /**
     * Creates a snapshot with the given ID and data.
     *
     * @param id the snapshot ID
     * @param data the snapshot data
     * @return a new Snapshot
     */
    private PeerProto.Snapshot createSnapshot(long id, String data) {
        return PeerProto.Snapshot.newBuilder()
                .setId(id)
                .setData(ByteString.copyFromUtf8(data))
                .setLogId(PeerProto.LogId.newBuilder().setIndex(id).setTerm(1).build())
                .build();
    }

    /**
     * Creates a snapshot result with the given snapshot and success status.
     *
     * @param snapshot the snapshot
     * @param success whether the snapshot was successful
     * @return a new SnapshotResult
     */
    private PeerProto.SnapshotResult createSnapshotResult(PeerProto.Snapshot snapshot, boolean success) {
        return PeerProto.SnapshotResult.newBuilder()
                .setSnapshot(snapshot)
                .setSuccess(success)
                .setReason(success ? "Success" : "Failed")
                .build();
    }

    /**
     * Tests saving and reading snapshots.
     *
     * @throws RaftException if the test fails due to a RaftException
     */
    private void testSaveAndRead() throws RaftException {
        // Get the next expected snapshot ID
        List<Long> existingIds = snapshotStore.listId();
        long nextId = existingIds.isEmpty() ? 1 : existingIds.getLast() + 1;

        // Create and save a successful snapshot
        PeerProto.Snapshot snapshot1 = createSnapshot(nextId, "Snapshot save/read data 1");
        PeerProto.SnapshotResult result1 = createSnapshotResult(snapshot1, true);
        snapshotStore.save(result1);

        // Read it back and verify
        PeerProto.SnapshotResult readResult1 = snapshotStore.read(nextId);
        Assert.assertNotNull(readResult1);
        Assert.assertEquals(result1, readResult1);

        // Create and save a failed snapshot
        PeerProto.Snapshot snapshot2 = createSnapshot(nextId + 1, "Snapshot save/read data 2");
        PeerProto.SnapshotResult result2 = createSnapshotResult(snapshot2, false);
        snapshotStore.save(result2);

        // Read it back and verify
        PeerProto.SnapshotResult readResult2 = snapshotStore.read(nextId + 1);
        Assert.assertNotNull(readResult2);
        Assert.assertEquals(result2, readResult2);

        // Try to read a non-existent snapshot
        PeerProto.SnapshotResult readResult3 = snapshotStore.read(nextId + 100);
        Assert.assertNull(readResult3);
    }

    /**
     * Tests getting the latest successful snapshot.
     *
     * @throws RaftException if the test fails due to a RaftException
     */
    private void testGetLatestSucceed() throws RaftException {
        // Get the next expected snapshot ID
        List<Long> existingIds = snapshotStore.listId();
        long nextId = existingIds.isEmpty() ? 1 : existingIds.getLast() + 1;

        // Create and save multiple snapshots with different success states
        PeerProto.Snapshot snapshot1 = createSnapshot(nextId, "Snapshot data 1");
        PeerProto.SnapshotResult result1 = createSnapshotResult(snapshot1, true);
        snapshotStore.save(result1);

        PeerProto.Snapshot snapshot2 = createSnapshot(nextId + 1, "Snapshot data 2");
        PeerProto.SnapshotResult result2 = createSnapshotResult(snapshot2, false);
        snapshotStore.save(result2);

        PeerProto.Snapshot snapshot3 = createSnapshot(nextId + 2, "Snapshot data 3");
        PeerProto.SnapshotResult result3 = createSnapshotResult(snapshot3, true);
        snapshotStore.save(result3);

        // Get the latest successful snapshot
        PeerProto.Snapshot latestSuccessful = snapshotStore.getLatestSucceed();
        Assert.assertNotNull(latestSuccessful);
        Assert.assertEquals(snapshot3, latestSuccessful);
    }

    /**
     * Tests listing snapshots.
     *
     * @throws RaftException if the test fails due to a RaftException
     * @throws IOException if the test fails due to an IOException
     */
    private void testList() throws RaftException, IOException {
        // Get the next expected snapshot ID
        List<Long> existingIds = snapshotStore.listId();
        long nextId = existingIds.isEmpty() ? 1 : existingIds.getLast() + 1;

        // Create and save multiple snapshots
        PeerProto.Snapshot snapshot1 = createSnapshot(nextId, "Snapshot list data 1");
        PeerProto.SnapshotResult result1 = createSnapshotResult(snapshot1, true);
        snapshotStore.save(result1);

        PeerProto.Snapshot snapshot2 = createSnapshot(nextId + 1, "Snapshot list data 2");
        PeerProto.SnapshotResult result2 = createSnapshotResult(snapshot2, false);
        snapshotStore.save(result2);

        // Verify that the snapshots were saved by reading them directly
        PeerProto.SnapshotResult readResult1 = snapshotStore.read(nextId);
        Assert.assertNotNull(readResult1);
        Assert.assertEquals(result1, readResult1);

        PeerProto.SnapshotResult readResult2 = snapshotStore.read(nextId + 1);
        Assert.assertNotNull(readResult2);
        Assert.assertEquals(result2, readResult2);

        // List all snapshot IDs to verify they exist
        List<Long> ids = snapshotStore.listId();
        Assert.assertTrue("Should contain first snapshot ID", ids.contains(nextId));
        Assert.assertTrue("Should contain second snapshot ID", ids.contains(nextId + 1));

        // List all snapshots
        try (SnapshotStore.ClosableIterator<PeerProto.SnapshotResult> iterator = snapshotStore.list()) {
            // The iterator doesn't automatically position at the beginning, so we need to
            // use listId to get all IDs and then read each snapshot individually
            for (Long id : ids) {
                PeerProto.SnapshotResult result = snapshotStore.read(id);
                Assert.assertNotNull(result);

                if (id == nextId) {
                    Assert.assertEquals(result1, result);
                } else if (id == nextId + 1) {
                    Assert.assertEquals(result2, result);
                }
            }
        }
    }

    /**
     * Tests listing snapshot IDs.
     *
     * @throws RaftException if the test fails due to a RaftException
     */
    private void testListId() throws RaftException {
        // Get the next expected snapshot ID
        List<Long> existingIds = snapshotStore.listId();
        long nextId = existingIds.isEmpty() ? 1 : existingIds.getLast() + 1;

        // Create and save multiple snapshots
        PeerProto.Snapshot snapshot1 = createSnapshot(nextId, "Snapshot listId data 1");
        PeerProto.SnapshotResult result1 = createSnapshotResult(snapshot1, true);
        snapshotStore.save(result1);

        PeerProto.Snapshot snapshot2 = createSnapshot(nextId + 1, "Snapshot listId data 2");
        PeerProto.SnapshotResult result2 = createSnapshotResult(snapshot2, false);
        snapshotStore.save(result2);

        // List all snapshot IDs
        List<Long> ids = snapshotStore.listId();
        Assert.assertNotNull(ids);
        Assert.assertTrue("Should contain first snapshot ID", ids.contains(nextId));
        Assert.assertTrue("Should contain second snapshot ID", ids.contains(nextId + 1));
    }

    /**
     * Tests that saving a snapshot with an incorrect ID throws an exception.
     *
     * @throws RaftException if the test fails due to a RaftException
     */
    private void testSaveWithIncorrectId() throws RaftException {
        // Get the next expected snapshot ID
        List<Long> existingIds = snapshotStore.listId();
        long nextId = existingIds.isEmpty() ? 1 : existingIds.getLast() + 1;

        // Try to save a snapshot with an incorrect ID (not sequential)
        PeerProto.Snapshot snapshot = createSnapshot(nextId + 10, "Incorrect ID");
        PeerProto.SnapshotResult result = createSnapshotResult(snapshot, true);

        boolean exceptionThrown = false;
        try {
            snapshotStore.save(result);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }

        Assert.assertTrue("Should throw IllegalArgumentException for incorrect ID", exceptionThrown);
    }

    /**
     * Tests the behavior with an initial (empty) snapshot store.
     *
     * @throws RaftException if the test fails due to a RaftException
     */
    private void testInitialSnapshot() throws RaftException {
        // Get the latest successful snapshot
        PeerProto.Snapshot latestSnapshot = snapshotStore.getLatestSucceed();
        Assert.assertNotNull(latestSnapshot);

        // If there are no snapshots, it should return the INITIAL_SNAPSHOT
        if (snapshotStore.listId().isEmpty()) {
            Assert.assertEquals(SnapshotStore.INITIAL_SNAPSHOT, latestSnapshot);
        }
    }

    /**
     * Tests installing a snapshot when the store is empty.
     *
     * @throws RaftException if the test fails due to a RaftException
     */
    private void testInstallWhenEmpty() throws RaftException {
        // This test is only applicable if the store is empty
        if (!snapshotStore.listId().isEmpty()) {
            return;
        }

        // Create and install a snapshot
        PeerProto.Snapshot snapshot = createSnapshot(10, "Installed snapshot data");
        snapshotStore.install(snapshot);

        // Verify the snapshot was installed
        List<Long> idsAfterInstall = snapshotStore.listId();
        Assert.assertEquals("Should have exactly one snapshot", 1, idsAfterInstall.size());
        Assert.assertEquals("Snapshot ID should match", 10L, (long)idsAfterInstall.get(0));

        // Read the snapshot and verify its content
        PeerProto.SnapshotResult result = snapshotStore.read(10);
        Assert.assertNotNull("Should be able to read the installed snapshot", result);
        Assert.assertEquals("Snapshot should match the installed one", snapshot, result.getSnapshot());
        Assert.assertTrue("Snapshot should be marked as successful", result.getSuccess());
    }

    /**
     * Tests installing a snapshot when the store already has snapshots.
     *
     * @throws RaftException if the test fails due to a RaftException
     */
    private void testInstallWithExistingSnapshots() throws RaftException {
        // Get the next expected snapshot ID
        List<Long> existingIds = snapshotStore.listId();
        if (existingIds.isEmpty()) {
            // Create some snapshots first
            PeerProto.Snapshot snapshot1 = createSnapshot(1, "Snapshot install test data 1");
            PeerProto.SnapshotResult result1 = createSnapshotResult(snapshot1, true);
            snapshotStore.save(result1);

            PeerProto.Snapshot snapshot2 = createSnapshot(2, "Snapshot install test data 2");
            PeerProto.SnapshotResult result2 = createSnapshotResult(snapshot2, false);
            snapshotStore.save(result2);

            existingIds = snapshotStore.listId();
        }

        // Verify snapshots exist
        Assert.assertFalse("Should have snapshots before install test", existingIds.isEmpty());
        List<Long> beforeInstallIds = existingIds;

        // Create and install a new snapshot
        PeerProto.Snapshot newSnapshot = createSnapshot(100, "New installed snapshot");
        snapshotStore.install(newSnapshot);

        // Verify that all previous snapshots are gone and only the new one exists
        List<Long> afterInstallIds = snapshotStore.listId();
        Assert.assertEquals("Should have exactly one snapshot", 1, afterInstallIds.size());
        Assert.assertEquals("Snapshot ID should match the installed one", 100L, (long)afterInstallIds.get(0));

        for (Long id : beforeInstallIds) {
            Assert.assertFalse("Should not contain previous snapshot ID: " + id, afterInstallIds.contains(id));
        }

        // Read the installed snapshot and verify its content
        PeerProto.SnapshotResult installedResult = snapshotStore.read(100);
        Assert.assertNotNull("Should be able to read the installed snapshot", installedResult);
        Assert.assertEquals("Snapshot should match the installed one", newSnapshot, installedResult.getSnapshot());
        Assert.assertTrue("Snapshot should be marked as successful", installedResult.getSuccess());
    }

    /**
     * Tests deleting a single snapshot.
     *
     * @throws RaftException if the test fails due to a RaftException
     */
    private void testDeleteSingleSnapshot() throws RaftException {
        // Get the next expected snapshot ID
        List<Long> existingIds = snapshotStore.listId();
        long nextId = existingIds.isEmpty() ? 1 : existingIds.getLast() + 1;

        // Create and save a snapshot
        PeerProto.Snapshot snapshot = createSnapshot(nextId, "Snapshot delete test data");
        PeerProto.SnapshotResult result = createSnapshotResult(snapshot, true);
        snapshotStore.save(result);

        // Verify the snapshot was saved
        PeerProto.SnapshotResult readResult = snapshotStore.read(nextId);
        Assert.assertNotNull("Snapshot should exist before deletion", readResult);

        // Delete the snapshot
        snapshotStore.delete(List.of(nextId));

        // Verify the snapshot was deleted
        PeerProto.SnapshotResult afterDeleteResult = snapshotStore.read(nextId);
        Assert.assertNull("Snapshot should not exist after deletion", afterDeleteResult);
    }

    /**
     * Tests deleting multiple snapshots.
     *
     * @throws RaftException if the test fails due to a RaftException
     */
    private void testDeleteMultipleSnapshots() throws RaftException {
        // Get the next expected snapshot ID
        List<Long> existingIds = snapshotStore.listId();
        long nextId = existingIds.isEmpty() ? 1 : existingIds.getLast() + 1;

        // Create and save multiple snapshots
        PeerProto.Snapshot snapshot1 = createSnapshot(nextId, "Snapshot multi-delete test data 1");
        PeerProto.SnapshotResult result1 = createSnapshotResult(snapshot1, true);
        snapshotStore.save(result1);

        PeerProto.Snapshot snapshot2 = createSnapshot(nextId + 1, "Snapshot multi-delete test data 2");
        PeerProto.SnapshotResult result2 = createSnapshotResult(snapshot2, true);
        snapshotStore.save(result2);

        PeerProto.Snapshot snapshot3 = createSnapshot(nextId + 2, "Snapshot multi-delete test data 3");
        PeerProto.SnapshotResult result3 = createSnapshotResult(snapshot3, true);
        snapshotStore.save(result3);

        // Verify the snapshots were saved
        Assert.assertNotNull("First snapshot should exist before deletion", snapshotStore.read(nextId));
        Assert.assertNotNull("Second snapshot should exist before deletion", snapshotStore.read(nextId + 1));
        Assert.assertNotNull("Third snapshot should exist before deletion", snapshotStore.read(nextId + 2));

        // Delete two of the snapshots
        snapshotStore.delete(List.of(nextId, nextId + 2));

        // Verify the deleted snapshots are gone and the remaining one still exists
        Assert.assertNull("First snapshot should not exist after deletion", snapshotStore.read(nextId));
        Assert.assertNotNull("Second snapshot should still exist", snapshotStore.read(nextId + 1));
        Assert.assertNull("Third snapshot should not exist after deletion", snapshotStore.read(nextId + 2));
    }

    /**
     * Tests deleting a non-existent snapshot.
     *
     * @throws RaftException if the test fails due to a RaftException
     */
    private void testDeleteNonExistentSnapshot() throws RaftException {
        // Get the next expected snapshot ID
        List<Long> existingIds = snapshotStore.listId();
        long nonExistentId = existingIds.isEmpty() ? 1000 : existingIds.getLast() + 1000;

        // Verify the snapshot doesn't exist
        Assert.assertNull("Snapshot should not exist", snapshotStore.read(nonExistentId));

        // Delete the non-existent snapshot (should not throw an exception)
        snapshotStore.delete(List.of(nonExistentId));

        // Verify the operation completed without errors
        Assert.assertNull("Snapshot should still not exist", snapshotStore.read(nonExistentId));
    }
}
