package io.axor.runtime.scheduler;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HashedWheelSchedulerTest {

    @Test
    public void testSetTimeoutCompletesNormally() throws Exception {
        HashedWheelTimer timer = new HashedWheelTimer();
        HashedWheelScheduler scheduler = new HashedWheelScheduler(timer);
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> scheduledFuture = scheduler.setTimeout(future, 100,
                TimeUnit.MILLISECONDS);
        future.complete("Result");
        assertEquals("Result", scheduledFuture.get(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSetTimeoutWithException() throws Exception {
        HashedWheelTimer timer = new HashedWheelTimer();
        HashedWheelScheduler scheduler = new HashedWheelScheduler(timer);
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> scheduledFuture = scheduler.setTimeout(future, 100,
                TimeUnit.MILLISECONDS);
        future.completeExceptionally(new RuntimeException("Failed"));
        try {
            scheduledFuture.get(200, TimeUnit.MILLISECONDS);
            fail("Expected ExecutionException to be thrown");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof RuntimeException);
            assertEquals("Failed", e.getCause().getMessage());
        }
    }

    @Test
    public void testSetTimeoutAlreadyCompleted() throws Exception {
        HashedWheelTimer timer = new HashedWheelTimer();
        HashedWheelScheduler scheduler = new HashedWheelScheduler(timer);
        CompletableFuture<String> future = CompletableFuture.completedFuture("Already Completed");
        CompletableFuture<String> scheduledFuture = scheduler.setTimeout(future, 100,
                TimeUnit.MILLISECONDS);
        assertEquals("Already Completed", scheduledFuture.get(200, TimeUnit.MILLISECONDS));
    }

    @Test(expected = TimeoutException.class)
    public void testSetTimeoutWithTimeoutException() throws Exception {
        HashedWheelTimer timer = new HashedWheelTimer();
        HashedWheelScheduler scheduler = new HashedWheelScheduler(timer);
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> scheduledFuture = scheduler.setTimeout(future, 100,
                TimeUnit.MILLISECONDS);
        scheduledFuture.get(50, TimeUnit.MILLISECONDS);
        fail("Expected TimeoutException to be thrown");
    }

    @Test
    public void testSetTimeoutCancelledBeforeCompletion() throws Exception {
        HashedWheelTimer timer = new HashedWheelTimer();
        HashedWheelScheduler scheduler = new HashedWheelScheduler(timer);
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> scheduledFuture = scheduler.setTimeout(future, 100,
                TimeUnit.MILLISECONDS);
        scheduledFuture.cancel(true);
        assertTrue(scheduledFuture.isCancelled());
    }
}
