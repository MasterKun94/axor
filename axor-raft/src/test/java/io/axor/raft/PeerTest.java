package io.axor.raft;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.api.impl.ActorUnsafe;
import io.axor.commons.config.ConfigMapper;
import io.axor.raft.logging.RaftLoggingFactory;
import io.axor.raft.logging.RocksdbRaftLoggingFactory;
import io.axor.raft.proto.PeerProto.PeerMessage;
import io.axor.runtime.EventDispatcher;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.List;

public class PeerTest {
    private static List<Peer> peers;
    private static RaftLoggingFactory factory;
    private static ActorSystem system;
    private static ActorRef<PeerMessage> peer1, peer2, peer3;

    @BeforeClass
    public static void setup() throws Exception {
        Config config = ConfigFactory
                .load(ConfigFactory.parseString("""
                        axor.network.bind.host = localhost
                        axor.network.bind.port = 4312
                        """))
                .resolve();
        system = ActorSystem.create("PeerTest", config);
        peers = List.of(
                new Peer(1, system.address("Peer1")),
                new Peer(2, system.address("Peer2")),
                new Peer(3, system.address("Peer3"))
        );
        FileUtils.deleteDirectory(new File(".tmp/PeerTest"));
        FileUtils.createParentDirectories(new File(".tmp/PeerTest"));
        factory = new RocksdbRaftLoggingFactory(ConfigFactory.parseString("""
                path = .tmp/PeerTest
                bufferDirect = true
                bufferMax = 4k
                dbOptions {
                  create_if_missing = true
                }
                """));
        RaftConfig raftConfig = ConfigMapper.map(ConfigFactory.parseString("""
                
                """), RaftConfig.class);
        EventDispatcher dispatcher = system.getDispatcherGroup().nextDispatcher();
        peer1 = system.start(c -> new RaftPeerActor(c, raftConfig, peers, 0, factory), "Peer1");
        peer2 = system.start(c -> new RaftPeerActor(c, raftConfig, peers, 1, factory), "Peer2");
        peer3 = system.start(c -> new RaftPeerActor(c, raftConfig, peers, 2, factory), "Peer3");

        ActorUnsafe.signal(peer1, RaftPeerActor.START_SIGNAL);
        Thread.sleep(500);
        ActorUnsafe.signal(peer2, RaftPeerActor.START_SIGNAL);
        Thread.sleep(100);
        ActorUnsafe.signal(peer3, RaftPeerActor.START_SIGNAL);
    }

    @AfterClass
    public static void cleanup() throws Exception {
        system.shutdownAsync().get();
        factory.close();
    }

    @Test
    public void test() throws Exception {
        Thread.sleep(20000);
    }

}
