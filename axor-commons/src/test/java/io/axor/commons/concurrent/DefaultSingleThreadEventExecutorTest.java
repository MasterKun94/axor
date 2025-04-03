package io.axor.commons.concurrent;

import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class DefaultSingleThreadEventExecutorTest {

    @Test
    public void testScheduleRunnable() throws InterruptedException, ExecutionException,
            TimeoutException {
        Runnable command = mock(Runnable.class);
        SingleThreadEventExecutor executor = new DefaultSingleThreadEventExecutor();
        ScheduledFuture<?> future = executor.schedule(command, 10, TimeUnit.MILLISECONDS);

        Thread.sleep(20);
        verify(command, times(1)).run();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        executor.shutdown();
    }

    @Test
    public void testScheduleCallable() throws InterruptedException, ExecutionException,
            TimeoutException {
        Callable<String> callable = () -> "test";
        SingleThreadEventExecutor executor = new DefaultSingleThreadEventExecutor();
        ScheduledFuture<String> future = executor.schedule(callable, 10, TimeUnit.MILLISECONDS);

        String result = future.get(20, TimeUnit.MILLISECONDS);
        assertEquals("test", result);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        executor.shutdown();
    }

    @Test
    public void testScheduleAtFixedRate() throws InterruptedException {
        Runnable command = mock(Runnable.class);
        SingleThreadEventExecutor executor = new DefaultSingleThreadEventExecutor();
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(command, 10, 10,
                TimeUnit.MILLISECONDS);

        Thread.sleep(30);
        verify(command, atLeast(2)).run();
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        future.cancel(true);
        assertTrue(future.isCancelled());
        executor.shutdown();
    }

    @Test
    public void testScheduleWithFixedDelay() throws InterruptedException {
        Runnable command = mock(Runnable.class);
        SingleThreadEventExecutor executor = new DefaultSingleThreadEventExecutor();
        ScheduledFuture<?> future = executor.scheduleWithFixedDelay(command, 10, 10,
                TimeUnit.MILLISECONDS);

        Thread.sleep(30);
        verify(command, atLeast(2)).run();
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        future.cancel(true);
        assertTrue(future.isCancelled());
        executor.shutdown();
    }

    @Test
    public void testCancelBeforeExecution() throws InterruptedException {
        Runnable command = mock(Runnable.class);
        SingleThreadEventExecutor executor = new DefaultSingleThreadEventExecutor();
        ScheduledFuture<?> future = executor.schedule(command, 50, TimeUnit.MILLISECONDS);

        future.cancel(true);
        assertTrue(future.isCancelled());

        Thread.sleep(60);
        verify(command, never()).run();
        executor.shutdown();
    }

    @Test
    public void test() throws Exception {
        new EventExecutorTestKit(new DefaultSingleThreadEventExecutor()).test();
    }

    @Test
    public void testTimeoutCompletableFuture() throws Exception {
        SingleThreadEventExecutor executor = new DefaultSingleThreadEventExecutor();
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> timeoutFuture = executor.timeout(future, 50,
                TimeUnit.MILLISECONDS);

        assertFalse(timeoutFuture.isDone());
        Thread.sleep(60);
        assertTrue(timeoutFuture.isCompletedExceptionally());
        assertTrue(timeoutFuture.isDone());
        try {
            timeoutFuture.get();
            fail("Expected TimeoutException");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof TimeoutException);
        }
    }

    @Test
    public void testTimeoutEventPromise() throws Exception {
        SingleThreadEventExecutor executor = new DefaultSingleThreadEventExecutor();
        EventPromise<String> promise = new DefaultEventPromise<>(executor);
        EventPromise<String> timeoutPromise = executor.timeout(promise, 50, TimeUnit.MILLISECONDS);

        assertFalse(timeoutPromise.isDone());
        Thread.sleep(60);
        assertTrue(timeoutPromise.isFailure());
        assertTrue(timeoutPromise.isDone());
        try {
            promise.toFuture().get();
            fail("Expected TimeoutException");
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e.getCause() instanceof TimeoutException);
        }
    }
}
