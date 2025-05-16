package io.axor.raft.logging;

import com.typesafe.config.ConfigFactory;
import io.axor.raft.RaftException;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.rocksdb.RocksDB;

import java.io.File;
import java.io.IOException;

/**
 * Tests for the RocksdbSnapshotStore implementation.
 * This test uses SnapshotStoreTestkit to test the functionality.
 */
public class RocksdbSnapshotStoreTest {
    private static RocksdbRaftLoggingFactory factory;
    private static SnapshotStore snapshotStore;

    static {
        RocksDB.loadLibrary();
    }

    @BeforeClass
    public static void setup() throws Exception {
        FileUtils.createParentDirectories(new File(".tmp/snapshottest"));
        FileUtils.deleteDirectory(new File(".tmp/snapshottest"));
        factory = new RocksdbRaftLoggingFactory(ConfigFactory.parseString("""
                path = .tmp/snapshottest
                bufferDirect = true
                bufferMax = 4k
                dbOptions {
                  create_if_missing = true
                }
                """));
        snapshotStore = factory.createSnapshotStore("test");
    }

    @AfterClass
    public static void cleanup() throws Exception {
        factory.close();
        FileUtils.deleteDirectory(new File(".tmp/snapshottest"));
    }

    @Test
    public void test() throws RaftException, IOException {
        // Run all tests using the testkit
        new SnapshotStoreTestkit(snapshotStore).test();
    }
}
