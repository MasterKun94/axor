package io.axor.raft.file;

import com.google.protobuf.ByteString;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FileWriteSubscriberTest {

    private static final File testFile = new File(".tmp/testfile3");
    private static byte[] testBytes;

    @BeforeClass
    public static void setup() throws Exception {
        testBytes = new byte[1024 * 1024 + 123];
        ThreadLocalRandom.current().nextBytes(testBytes);
        FileUtils.forceMkdirParent(testFile);
        FileUtils.deleteQuietly(testFile);
    }

    @AfterClass
    public static void cleanup() {
        testBytes = null;
    }

    @Test
    public void test() throws Exception {
        FileWriteSubscriber handler = new FileWriteSubscriber(testFile);
        handler.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                throw new IllegalArgumentException();
            }
        });
        ByteArrayInputStream b = new ByteArrayInputStream(testBytes);
        ByteString chunk;
        while (!(chunk = ByteString.readFrom(b)).isEmpty()) {
            handler.onNext(chunk);
        }
        handler.onComplete();
        handler.getFuture().join();
        byte[] bytes = FileUtils.readFileToByteArray(testFile);
        assertArrayEquals(testBytes, bytes);
    }


    /**
     * Test class for FileWriteHandler. The FileWriteHandler class is responsible for writing
     * ByteString data to a file asynchronously. This test class focuses on the `getFuture` method,
     * which provides a CompletableFuture that completes when the file writing process is finished
     * or fails.
     */

    @Test
    public void testGetFutureCompletesOnSuccessfulWrite() throws IOException {
        // Create a temporary file for testing
        File tempFile = Files.createTempFile("test", ".tmp").toFile();
        tempFile.deleteOnExit();

        // Create a mock subscription
        Flow.Subscription subscription = Mockito.mock(Flow.Subscription.class);

        // Initialize the FileWriteHandler
        FileWriteSubscriber handler = new FileWriteSubscriber(tempFile);

        // Simulate subscription
        handler.onSubscribe(subscription);

        // Simulate data writing
        handler.onNext(ByteString.copyFromUtf8("test data"));

        // Simulate completion
        handler.onComplete();

        // Retrieve the future and verify it completes successfully
        CompletableFuture<Void> future = handler.getFuture();
        try {
            future.join(); // Wait for the future to complete
            assertTrue("Future should complete successfully", future.isDone());
        } catch (Exception e) {
            fail("Future should not throw an exception: " + e.getMessage());
        }
    }

    @Test
    public void testGetFutureFailsOnError() throws IOException {
        // Create a temporary file for testing
        File tempFile = Files.createTempFile("test", ".tmp").toFile();
        tempFile.deleteOnExit();

        // Create a mock subscription
        Flow.Subscription subscription = Mockito.mock(Flow.Subscription.class);

        // Initialize the FileWriteHandler
        FileWriteSubscriber handler = new FileWriteSubscriber(tempFile);

        // Simulate subscription
        handler.onSubscribe(subscription);

        // Simulate an error during writing
        RuntimeException error = new RuntimeException("Simulated error");
        handler.onError(error);

        // Retrieve the future and verify it completes exceptionally
        CompletableFuture<Void> future = handler.getFuture();
        try {
            future.join(); // This should throw an exception
            fail("Future should have completed exceptionally");
        } catch (Exception e) {
            assertTrue("Future should complete exceptionally with the correct error",
                    e.getCause() instanceof RuntimeException && e.getCause().getMessage().equals(
                            "Simulated error"));
        }
    }
}
