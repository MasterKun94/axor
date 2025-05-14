package io.axor.raft.file;

import io.axor.api.ActorAddress;
import io.axor.raft.proto.FileManagerProto;
import io.axor.runtime.MsgType;
import io.axor.testkit.actor.ActorTestKit;
import io.axor.testkit.actor.MockActorRef;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class FileReadTaskTest {

    private static final File testFile = new File(".tmp/testfile");
    static ActorTestKit testKit = new ActorTestKit(Duration.ofMillis(500));
    private static ScheduledExecutorService ec;
    private static byte[] testBytes;

    @BeforeClass
    public static void setup() throws Exception {
        ec = Executors.newScheduledThreadPool(10);
        testBytes = new byte[1024 * 1024 + 123];
        ThreadLocalRandom.current().nextBytes(testBytes);
        FileUtils.forceMkdirParent(testFile);
        FileUtils.deleteQuietly(testFile);
        FileUtils.writeByteArrayToFile(testFile, testBytes);
    }

    @AfterClass
    public static void cleanup() {
        testBytes = null;
        ec.shutdown();
        FileUtils.deleteQuietly(testFile);
    }

    @Test
    public void test() throws Exception {
        MockActorRef<FileManagerProto.FileServerMessage> sender = testKit.mock(
                ActorAddress.create("test@localhost:1234/sender"),
                MsgType.of(FileManagerProto.FileServerMessage.class));
        MockActorRef<FileManagerProto.FileClientMessage> receiver = testKit.mock(
                ActorAddress.create("test@localhost:1234/receiver"),
                MsgType.of(FileManagerProto.FileClientMessage.class));
        TokenBucket bucket = new TokenBucket(8192 * 8, 8192 * 16);
        FileReadTask task = new FileReadTask("test_task", ec, bucket, testFile, sender, receiver);
        task.run();
        long start = System.currentTimeMillis();
        int seqId = 0;
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        Thread.sleep(100);
        while (true) {
            var poll = receiver.poll();
            assertEquals(poll.getSender(), sender);
            var msg = poll.getMsg();
            switch (msg.getMsgCase()) {
                case ENTRY -> {
                    assertEquals(seqId, msg.getEntry().getSeqId());
                    msg.getEntry().getData().writeTo(bout);
                    seqId++;
                    continue;
                }
                case COMPLETE -> {
                    Assert.assertTrue(msg.getComplete().getSuccess());
                    Assert.assertEquals(testBytes.length, msg.getComplete().getTotalLength());
                }
                default -> throw new IllegalArgumentException();
            }
            break;
        }
        System.out.println(System.currentTimeMillis() - start);
        sender.expectSignal(new FileReadTask.TaskStopSignal("test_task"));
        assertArrayEquals(testBytes, bout.toByteArray());
    }
}
