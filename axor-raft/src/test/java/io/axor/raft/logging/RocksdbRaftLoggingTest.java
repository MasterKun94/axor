package io.axor.raft.logging;

import com.typesafe.config.ConfigFactory;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;

import java.io.File;

public class RocksdbRaftLoggingTest {
    private static RocksdbRaftLoggingFactory factory;

    static {
        RocksDB.loadLibrary();
    }

    @BeforeClass
    public static void setup() throws Exception {
        FileUtils.createParentDirectories(new File(".tmp/raftlog"));
        Options opt = new Options();
        opt.setCreateIfMissing(true);
        FileUtils.deleteDirectory(new File(".tmp/raftlog"));
        factory = new RocksdbRaftLoggingFactory(ConfigFactory.parseString("""
                path = .tmp/raftlog
                bufferDirect = true
                bufferMax = 4k
                dbOptions {
                  create_if_missing = true
                }
                """));
    }

    @AfterClass
    public static void cleanup() throws Exception {
        factory.close();
    }

    @Test
    public void test() throws Exception {
        new RaftLoggingTestkit(factory.createLogging("test1")).test();
        new RaftLoggingTestkit(factory.createLogging("test2")).test();
        Assert.assertThrows(IllegalArgumentException.class, () -> factory.createLogging("test2"));
        factory.close();
        factory = new RocksdbRaftLoggingFactory(ConfigFactory.parseString("""
                path = .tmp/raftlog
                bufferDirect = true
                bufferMax = 4k
                dbOptions {
                  create_if_missing = true
                }
                """));
        new RaftLoggingTestkit(factory.createLogging("test3")).test();
        new RaftLoggingTestkit(factory.createLogging("test1")).testContinue();
        new RaftLoggingTestkit(factory.createLogging("test2")).testContinue();
    }
}
