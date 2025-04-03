package io.axor.commons.concurrent;

import org.junit.Test;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FailedEventStageTest {

    @Test
    public void testTransformInExecutorWithSuccess() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(true);

        Function<Try<String>, Try<Integer>> transformer = t -> Try.success(42);
        FailedEventStage<String> failedEventStage = new FailedEventStage<>(new RuntimeException(
                "test"), executor);
        EventStage<Integer> result = failedEventStage.transform(transformer, executor);

        assertTrue(result instanceof SucceedEventStage);
        assertEquals(42, result.getResult().value().intValue());
    }

    @Test
    public void testTransformInExecutorWithFailure() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(true);

        Function<Try<String>, Try<Integer>> transformer = t -> Try.failure(new RuntimeException(
                "transformed"));
        FailedEventStage<String> failedEventStage = new FailedEventStage<>(new RuntimeException(
                "test"), executor);
        EventStage<Integer> result = failedEventStage.transform(transformer, executor);

        assertTrue(result instanceof FailedEventStage);
        assertEquals("transformed", result.getResult().cause().getMessage());
    }

    @Test
    public void testTransformInExecutorWithException() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(true);

        Function<Try<String>, Try<Integer>> transformer = t -> {
            throw new RuntimeException("exception");
        };
        FailedEventStage<String> failedEventStage = new FailedEventStage<>(new RuntimeException(
                "test"), executor);
        EventStage<Integer> result = failedEventStage.transform(transformer, executor);

        assertTrue(result instanceof FailedEventStage);
        assertEquals("exception", result.getResult().cause().getMessage());
    }

    @Test
    public void testTransformNotInExecutorWithSuccess() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(false);

        Function<Try<String>, Try<Integer>> transformer = t -> Try.success(42);
        FailedEventStage<String> failedEventStage = new FailedEventStage<>(new RuntimeException(
                "test"), executor);
        EventStage<Integer> result = failedEventStage.transform(transformer, executor);

        assertTrue(result instanceof EventPromise);
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    public void testTransformNotInExecutorWithFailure() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(false);

        Function<Try<String>, Try<Integer>> transformer = t -> Try.failure(new RuntimeException(
                "transformed"));
        FailedEventStage<String> failedEventStage = new FailedEventStage<>(new RuntimeException(
                "test"), executor);
        EventStage<Integer> result = failedEventStage.transform(transformer, executor);

        assertTrue(result instanceof EventPromise);
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    public void testTransformNotInExecutorWithException() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(false);

        Function<Try<String>, Try<Integer>> transformer = t -> {
            throw new RuntimeException("exception");
        };
        FailedEventStage<String> failedEventStage = new FailedEventStage<>(new RuntimeException(
                "test"), executor);
        EventStage<Integer> result = failedEventStage.transform(transformer, executor);

        assertTrue(result instanceof EventPromise);
        verify(executor).execute(any(Runnable.class));
    }
}
