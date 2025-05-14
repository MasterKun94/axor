package io.axor.raft.file;

import com.google.protobuf.ByteString;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.api.impl.ActorUnsafe;
import io.axor.commons.config.ConfigMapper;
import io.axor.raft.proto.FileManagerProto;
import io.axor.raft.proto.FileManagerProto.FileServerMessage;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileActorTest {
    private static final File testFile = new File(".tmp/testFile2");
    private static final File targetFile = new File(".tmp/targetFile2");
    private static ActorSystem system;
    private static ScheduledExecutorService ec;
    private static byte[] testBytes;
    private static ActorRef<FileServerMessage> server;

    @BeforeClass
    public static void setup() throws Exception {
        Config config = ConfigFactory
                .load(ConfigFactory.parseString("""
                        axor.network.bind.host = localhost
                        axor.network.bind.port = 4313
                        """))
                .resolve();
        system = ActorSystem.create("FileTest", config);
        ec = Executors.newScheduledThreadPool(10);
        testBytes = new byte[1024 * 1024 + 123];
        ThreadLocalRandom.current().nextBytes(testBytes);
        FileUtils.forceMkdirParent(testFile);
        FileUtils.deleteQuietly(testFile);
        FileUtils.writeByteArrayToFile(testFile, testBytes);
        server = system.start(c -> new FileServerActor(c, ec),
                "fileServer");
    }

    @AfterClass
    public static void cleanup() {
        system.shutdownAsync();
        ec.shutdown();
        FileUtils.deleteQuietly(testFile);
        FileUtils.deleteQuietly(targetFile);
    }

    @Test
    public void test() throws Exception {
        TestReaderHandler handler = new TestReaderHandler();
        ActorRef<FileManagerProto.FileClientMessage> fileClient1 = system.start(
                actorContext -> new FileClientActor(actorContext, server, 8192 * 32,
                        testFile.getAbsolutePath(), handler),
                "fileClient1");
        Assert.assertNull(handler.f.join());
        Assert.assertArrayEquals(testBytes, handler.bout.toByteArray());
        Thread.sleep(100);
        Assert.assertTrue(ActorUnsafe.isStopped(fileClient1));

        TestReaderHandler handler2 = new TestReaderHandler();
        ActorRef<FileManagerProto.FileClientMessage> fileClient2 = system.start(
                actorContext -> new FileClientActor(actorContext, server, 8192 * 32, new File(
                        ".tmp/noop").getAbsolutePath(), handler2),
                "fileClient2");
        Assert.assertThrows(IOException.class, () -> {
            try {
                handler2.f.get();
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        });
        Thread.sleep(100);
        Assert.assertTrue(ActorUnsafe.isStopped(fileClient2));
    }

    @Test
    public void testFileService() throws Exception {
        FileService service = new FileService(system, server,
                ConfigMapper.map(ConfigFactory.empty(), FileConfig.class));
        service.copyTo(testFile.getAbsolutePath(), targetFile).join();
        Assert.assertArrayEquals(testBytes, FileUtils.readFileToByteArray(targetFile));
    }

    public static class TestReaderHandler implements Flow.Subscriber<ByteString> {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final CompletableFuture<Void> f = new CompletableFuture<>();
        final AtomicBoolean start = new AtomicBoolean();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Assert.assertTrue(start.compareAndSet(false, true));
        }

        @Override
        public void onNext(ByteString data) {
            Assert.assertTrue(start.get());
            try {
                data.writeTo(bout);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onComplete() {
            Assert.assertTrue(start.get());
            Assert.assertFalse(f.isDone());
            f.complete(null);
        }

        @Override
        public void onError(Throwable error) {
            Assert.assertTrue(start.get());
            Assert.assertFalse(f.isDone());
            f.completeExceptionally(error);
        }
    }
}
