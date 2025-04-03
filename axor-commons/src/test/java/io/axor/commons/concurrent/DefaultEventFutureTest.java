package io.axor.commons.concurrent;

import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultEventFutureTest {
    private static final EventExecutor executor = new DefaultSingleThreadEventExecutor();

    @Test
    public void testSyncResultSuccess() throws InterruptedException {
        DefaultEventFuture<String> future = new DefaultEventFuture<>(executor);
        future.doSuccess("success");
        Try<String> result = future.syncResult();
        assertTrue(result.isSuccess());
        assertEquals("success", result.value());
    }

    @Test
    public void testSyncResultFailure() throws InterruptedException {
        DefaultEventFuture<String> future = new DefaultEventFuture<>(executor);
        future.doFailed(new RuntimeException("failure"));
        Try<String> result = future.syncResult();
        assertTrue(result.isFailure());
        assertEquals(RuntimeException.class, result.cause().getClass());
        assertEquals("failure", result.cause().getMessage());
    }

    @Test
    public void testSyncResultWithTimeoutSuccess() throws InterruptedException {
        DefaultEventFuture<String> future = new DefaultEventFuture<>(executor);
        future.doSuccess("success");
        Try<String> result = future.syncResult(1, TimeUnit.SECONDS);
        assertTrue(result.isSuccess());
        assertEquals("success", result.value());
    }

    @Test
    public void testSyncResultWithTimeoutFailure() throws InterruptedException {
        DefaultEventFuture<String> future = new DefaultEventFuture<>(executor);
        future.doFailed(new RuntimeException("failure"));
        Try<String> result = future.syncResult(1, TimeUnit.SECONDS);
        assertTrue(result.isFailure());
        assertEquals(RuntimeException.class, result.cause().getClass());
        assertEquals("failure", result.cause().getMessage());
    }

    @Test
    public void testSyncResultWithTimeoutExceeded() throws InterruptedException {
        DefaultEventFuture<String> future = new DefaultEventFuture<>(executor);
        Try<String> result = future.syncResult(100, TimeUnit.MILLISECONDS);
        assertTrue(result.isFailure());
        assertEquals(TimeoutException.class, result.cause().getClass());
    }

    @Test
    public void testMapSuccess() throws InterruptedException {
        DefaultEventFuture<String> future = new DefaultEventFuture<>(executor);
        future.success("success");
        EventFuture<Integer> mappedFuture = future.map(String::length);
        Try<Integer> result = mappedFuture.syncResult();
        assertTrue(result.isSuccess());
        assertEquals(7, (int) result.value());
    }

    @Test
    public void testMapFailure() throws InterruptedException {
        DefaultEventFuture<String> future = new DefaultEventFuture<>(executor);
        future.failure(new RuntimeException("failure"));
        EventFuture<Integer> mappedFuture = future.map(String::length);
        Try<Integer> result = mappedFuture.syncResult();
        assertTrue(result.isFailure());
        assertEquals(RuntimeException.class, result.cause().getClass());
        assertEquals("failure", result.cause().getMessage());
    }

    @Test
    public void testFlatmapSuccess() throws Exception {
        DefaultEventFuture<String> future = new DefaultEventFuture<>(executor);
        future.success("success");
        EventFuture<Integer> flatMappedFuture =
                future.flatmap(value -> EventFuture.supplyAsync(value::length, executor));
        Try<Integer> result = flatMappedFuture.syncResult();
        if (result.isFailure()) {
            throw new RuntimeException(result.cause());
        }
        assertTrue(result.isSuccess());
        assertEquals(7, (int) result.value());
    }

    @Test
    public void testFlatmapFailure() throws InterruptedException {
        DefaultEventFuture<String> future = new DefaultEventFuture<>(executor);
        future.failure(new RuntimeException("failure"));
        EventFuture<Integer> flatMappedFuture =
                future.flatmap(value -> EventFuture.supplyAsync(value::length, executor));
        Try<Integer> result = flatMappedFuture.syncResult();
        assertTrue(result.isFailure());
        assertEquals(RuntimeException.class, result.cause().getClass());
        assertEquals("failure", result.cause().getMessage());
    }

    @Test
    public void testFlatmapWithTimeoutExceeded() throws InterruptedException {
        DefaultEventFuture<String> future = new DefaultEventFuture<>(executor);
        EventFuture<Integer> flatMappedFuture =
                future.flatmap(value -> EventFuture.supplyAsync(value::length, executor));
        Try<Integer> result = flatMappedFuture.syncResult(100, TimeUnit.MILLISECONDS);
        assertTrue(result.isFailure());
        assertEquals(TimeoutException.class, result.cause().getClass());
    }
}
