package io.axor.cp.raft;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.commons.config.ConfigMapper;
import io.axor.cp.messages.Peer;
import io.axor.cp.messages.RaftMessage;
import io.axor.cp.raft.logging.AsyncRaftLoggingFactoryAdaptor;
import io.axor.cp.raft.logging.RocksdbRaftLoggingFactory;
import io.axor.testkit.actor.ActorTestKit;
import io.axor.testkit.actor.MockActorRef;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.Assert.*;

public class ReplicatorTest {

    private static ActorSystem system;
    private static ActorTestKit testKit;
    private ActorRef<RaftMessage> replicator;

    @BeforeClass
    public static void setup() throws Exception {
        Config config1 = ConfigFactory
                .load(ConfigFactory.parseString("axor.network.bind.port = " + 10124))
                .resolve();
        system = ActorSystem.create("test", config1);
        testKit = new ActorTestKit(Duration.ofSeconds(1));
    }

    @BeforeClass
    public static void cleanup() throws Exception {
        if (system != null) {
            system.shutdownAsync().get();
        }
    }

    @Test
    public void test() throws Exception {
        testInit();
        testAppend();
        testCommit();
        testHeartbeat();
        testRead();
    }

    private void testInit() throws Exception {
        Config config = ConfigFactory.parseString("""
                path = .tmp/replicator
                bufferDirect = true
                bufferMax = 4k
                dbOptions {
                  create_if_missing = true
                }
                asyncWriterThreadNum=10
                asyncReaderThreadNum=10
                """);
        RaftConfig raftConfig = ConfigMapper.map(ConfigFactory.parseString("""
                
                """), RaftConfig.class);
        MockActorRef<RaftMessage> nodeRef = testKit.mock("testNode", RaftMessage.class, system);
        Peer peer = new Peer(1, 1, nodeRef.address());
        replicator = system.start(c -> new Replicator(c,
                "replicatorTest",
                new RaftContext(system, List.of(peer), peer, new RaftConfig(
                        Duration.ofSeconds(1),
                        100,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(15),
                        5)),
                new AsyncRaftLoggingFactoryAdaptor(new RocksdbRaftLoggingFactory(config), config),
                nodeRef
                ), "replicatorTest");
        nodeRef.expectSignal(new Replicator.ReplicatorLoadSuccess());
    }

    private void testAppend() throws Exception {

    }

    private void testCommit() throws Exception {
    }

    private void testHeartbeat() throws Exception {
    }

    private void testRead() throws Exception {
    }
}
