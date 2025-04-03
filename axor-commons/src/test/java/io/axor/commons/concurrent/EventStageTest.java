package io.axor.commons.concurrent;

import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EventStageTest {
    EventExecutor executor = new DefaultSingleThreadEventExecutor();

    @Test
    public void testSupplyAsyncSuccess() throws Exception {

        Callable<String> callable = () -> "success";
        EventStage<String> stage = EventStage.supplyAsync(callable, executor);

        CompletableFuture<String> future = stage.toCompletableFuture();
        assertEquals("success", future.get());
    }

    @Test
    public void testSupplyAsyncFailure() throws Exception {

        Callable<String> callable = () -> {
            throw new RuntimeException("failure");
        };
        EventStage<String> stage = EventStage.supplyAsync(callable, executor);

        CompletableFuture<String> future = stage.toCompletableFuture();
        try {
            future.get();
            fail("Expected exception to be thrown");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof RuntimeException);
            assertEquals("failure", e.getCause().getMessage());
        }
    }

    @Test
    public void testSupplyAsyncIsDone() throws Exception {

        Callable<String> callable = () -> "success";
        EventStage<String> stage = EventStage.supplyAsync(callable, executor);

        CompletableFuture<String> future = stage.toCompletableFuture();
        future.get();
        assertTrue(stage.isDone());
    }

    @Test
    public void testSupplyAsyncIsSuccess() throws Exception {

        Callable<String> callable = () -> "success";
        EventStage<String> stage = EventStage.supplyAsync(callable, executor);

        CompletableFuture<String> future = stage.toCompletableFuture();
        future.get();
        assertTrue(stage.isSuccess());
    }

    @Test
    public void testSupplyAsyncIsFailure() throws Exception {

        Callable<String> callable = () -> {
            throw new RuntimeException("failure");
        };
        EventStage<String> stage = EventStage.supplyAsync(callable, executor);

        CompletableFuture<String> future = stage.toCompletableFuture();
        try {
            future.get();
        } catch (Exception ignored) {
        }
        assertTrue(stage.isFailure());
    }

    @Test
    public void testRunAsyncSuccess() throws Exception {
        Runnable runnable = () -> {
        };
        EventStage<Void> stage = EventStage.runAsync(runnable, executor);

        CompletableFuture<Void> future = stage.toCompletableFuture();
        future.get();
        assertTrue(stage.isDone());
        assertTrue(stage.isSuccess());
    }

    @Test
    public void testRunAsyncFailure() throws Exception {
        Runnable runnable = () -> {
            throw new RuntimeException("failure");
        };
        EventStage<Void> stage = EventStage.runAsync(runnable, executor);

        CompletableFuture<Void> future = stage.toCompletableFuture();
        try {
            future.get();
            fail("Expected exception to be thrown");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof RuntimeException);
            assertEquals("failure", e.getCause().getMessage());
        }
    }

    @Test
    public void testRunAsyncIsDone() throws Exception {
        Runnable runnable = () -> {
        };
        EventStage<Void> stage = EventStage.runAsync(runnable, executor);

        CompletableFuture<Void> future = stage.toCompletableFuture();
        future.get();
        assertTrue(stage.isDone());
    }

    @Test
    public void testRunAsyncIsSuccess() throws Exception {
        Runnable runnable = () -> {
        };
        EventStage<Void> stage = EventStage.runAsync(runnable, executor);

        CompletableFuture<Void> future = stage.toCompletableFuture();
        future.get();
        assertTrue(stage.isSuccess());
    }

    @Test
    public void testRunAsyncIsFailure() throws Exception {
        Runnable runnable = () -> {
            throw new RuntimeException("failure");
        };
        EventStage<Void> stage = EventStage.runAsync(runnable, executor);

        CompletableFuture<Void> future = stage.toCompletableFuture();
        try {
            future.get();
        } catch (Exception ignored) {
        }
        assertTrue(stage.isFailure());
    }
}
