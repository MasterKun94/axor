package io.axor.cp.raft;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.commons.config.ConfigMapper;
import io.axor.cp.messages.AppendStatus;
import io.axor.cp.messages.CommitStatus;
import io.axor.cp.messages.LeaderState;
import io.axor.cp.messages.LogAppend;
import io.axor.cp.messages.LogAppendAck;
import io.axor.cp.messages.LogCommit;
import io.axor.cp.messages.LogCommitAck;
import io.axor.cp.messages.LogEntry;
import io.axor.cp.messages.LogEntryId;
import io.axor.cp.messages.LogFetch;
import io.axor.cp.messages.LogFetchRes;
import io.axor.cp.messages.Peer;
import io.axor.cp.messages.RaftMessage;
import io.axor.cp.raft.logging.AsyncRaftLoggingFactoryAdaptor;
import io.axor.cp.raft.logging.RocksdbRaftLoggingFactory;
import io.axor.testkit.actor.ActorTestKit;
import io.axor.testkit.actor.MockActorRef;
import org.apache.ratis.util.FileUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.time.Duration;
import java.util.List;

public class ReplicatorTest {

    private static ActorSystem system;
    private static ActorTestKit testKit;
    private ActorRef<RaftMessage> replicator;
    private MockActorRef<RaftMessage> nodeRef;

    @BeforeClass
    public static void setup() throws Exception {
        FileUtils.deleteFully(new File(".tmp/replicator"));
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

        testAppend2();
        testCommit2();
        testRead2();
    }

    private void testInit() {
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
                logAppendTimeout = 1s
                logAppendSizeLimit = 64k
                leaderHeartbeatInterval = 1s
                leaderHeartbeatTimeout = 5s
                followerIndexLagThreshold = 10
                """), RaftConfig.class);
        nodeRef = testKit.mock("testNode", RaftMessage.class, system);
        Peer peer = new Peer(1, 1, nodeRef.address());
        RaftContext raftContext = new RaftContext(system, List.of(peer), peer, raftConfig);
        raftContext.updateLeader(peer);
        replicator = system.start(c -> new Replicator(c,
                "replicatorTest",
                raftContext,
                new AsyncRaftLoggingFactoryAdaptor(new RocksdbRaftLoggingFactory(config), config),
                nodeRef
        ), "replicatorTest");
        nodeRef.expectSignal(new Replicator.ReplicatorLoadSuccess());
    }

    private void testAppend() throws Exception {
        replicator.tell(new LogAppend(
                123,
                List.of(new LogEntry(1, 1, "data1"),
                        new LogEntry(2, 1, "data2"),
                        new LogEntry(3, 1, "data3")
                ),
                LogEntryId.INITIAL
        ), nodeRef);
        nodeRef.expectReceive(new LogAppendAck(123, AppendStatus.SUCCESS, LogEntryId.INITIAL));
    }

    private void testCommit() throws Exception {
        replicator.tell(new LogCommit(
                124,
                new LogEntryId(3, 1),
                LogEntryId.INITIAL
        ), nodeRef);
        nodeRef.expectReceive(new LogCommitAck(124, CommitStatus.SUCCESS, new LogEntryId(3, 1)));
    }

    private void testHeartbeat() throws Exception {
        replicator.tell(new LogAppend(
                125,
                List.of(new LogEntry(4, 1, "data4"),
                        new LogEntry(5, 1, "data5"),
                        new LogEntry(6, 1, "data6")
                ),
                new LogEntryId(3, 1)
        ), nodeRef);
        nodeRef.expectReceive(new LogAppendAck(125, AppendStatus.SUCCESS, new LogEntryId(3, 1)));
        replicator.tell(new LeaderState(new LogEntryId(6, 1)), nodeRef);
        nodeRef.expectNoMsg();
    }

    private void testRead() throws Exception {
        replicator.tell(new LogFetch(126,
                new LogEntryId(3, 1), 3, 1024), nodeRef);
        nodeRef.expectReceive(new LogFetchRes(
                126,
                true,
                List.of(new LogEntry(3, 1, "data3"),
                        new LogEntry(4, 1, "data4"),
                        new LogEntry(5, 1, "data5")),
                "OK"
        ));
    }


    private void testAppend2() throws Exception {
        replicator.tell(new LogAppend(
                123,
                List.of(new LogEntry(8, 1, "data8"),
                        new LogEntry(9, 1, "data9")
                ),
                new LogEntryId(6, 1)
        ), nodeRef);
        nodeRef.expectReceive(new LogAppendAck(123, AppendStatus.INDEX_EXCEEDED, new LogEntryId(6
                , 1)));

        replicator.tell(new LogAppend(
                123,
                List.of(new LogEntry(7, 1, "data7"),
                        new LogEntry(9, 1, "data9")
                ),
                new LogEntryId(6, 1)
        ), nodeRef);
        nodeRef.expectReceive(new LogAppendAck(123, AppendStatus.INDEX_EXCEEDED, new LogEntryId(6
                , 1)));


        replicator.tell(new LogAppend(
                123,
                List.of(new LogEntry(7, 1, "data7"),
                        new LogEntry(8, 1, "data8"),
                        new LogEntry(9, 1, "data9")
                ),
                new LogEntryId(6, 1)
        ), nodeRef);
        nodeRef.expectReceive(new LogAppendAck(123, AppendStatus.SUCCESS, new LogEntryId(6, 1)));

        replicator.tell(new LogAppend(
                123,
                List.of(new LogEntry(7, 1, "data7x"),
                        new LogEntry(8, 1, "data8x")
                ),
                new LogEntryId(6, 1)
        ), nodeRef);
        nodeRef.expectReceive(new LogAppendAck(123, AppendStatus.SUCCESS, new LogEntryId(6, 1)));

        replicator.tell(new LogAppend(
                123,
                List.of(new LogEntry(7, 0, "data7x")
                ),
                new LogEntryId(6, 1)
        ), nodeRef);
        nodeRef.expectReceive(new LogAppendAck(123, AppendStatus.TERM_EXPIRED, new LogEntryId(6,
                1)));

        replicator.tell(new LogAppend(
                123,
                List.of(new LogEntry(6, 1, "data6x")
                ),
                new LogEntryId(6, 1)
        ), nodeRef);
        nodeRef.expectReceive(new LogAppendAck(123, AppendStatus.INDEX_EXPIRED, new LogEntryId(6,
                1)));

    }

    private void testCommit2() {
        replicator.tell(new LogCommit(
                124,
                new LogEntryId(9, 1),
                LogEntryId.INITIAL
        ), nodeRef);
        nodeRef.expectReceive(new LogCommitAck(124, CommitStatus.ILLEGAL_STATE, new LogEntryId(6,
                1)));

        replicator.tell(new LogCommit(
                124,
                new LogEntryId(8, 1),
                LogEntryId.INITIAL
        ), nodeRef);
        nodeRef.expectReceive(new LogCommitAck(124, CommitStatus.SUCCESS, new LogEntryId(8, 1)));
    }

    private void testRead2() throws Exception {
        replicator.tell(new LogFetch(126,
                new LogEntryId(7, 1), 3, 1024), nodeRef);
        nodeRef.expectReceive(new LogFetchRes(
                126,
                true,
                List.of(new LogEntry(7, 1, "data7x"),
                        new LogEntry(8, 1, "data8x")),
                "OK"
        ));
    }
}
