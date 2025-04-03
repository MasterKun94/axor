package io.axor.commons.concurrent;

import org.junit.Test;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SucceedEventStageTest {

    @Test
    public void testMapWithExecutorInExecutor() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(true);

        SucceedEventStage<Integer> stage = new SucceedEventStage<>(10, executor);
        Function<Integer, String> func = value -> "Value: " + value;

        EventStage<String> result = stage.map(func, executor);

        assertTrue(result instanceof SucceedEventStage);
        assertEquals("Value: 10", ((SucceedEventStage<String>) result).value);
    }

    @Test
    public void testMapWithExecutorNotInExecutor() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(false);

        SucceedEventStage<Integer> stage = new SucceedEventStage<>(10, executor);
        Function<Integer, String> func = value -> "Value: " + value;

        EventStage<String> result = stage.map(func, executor);

        assertTrue(result instanceof DefaultEventPromise);
        verify(executor, times(1)).execute(any());
    }

    @Test
    public void testMapWithExceptionInExecutor() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(true);

        SucceedEventStage<Integer> stage = new SucceedEventStage<>(10, executor);
        Function<Integer, String> func = value -> {
            throw new RuntimeException("Error");
        };

        EventStage<String> result = stage.map(func, executor);

        assertTrue(result instanceof FailedEventStage);
        assertEquals("Error", ((FailedEventStage) result).cause.getMessage());
    }

    @Test
    public void testMapWithExceptionNotInExecutor() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(false);

        SucceedEventStage<Integer> stage = new SucceedEventStage<>(10, executor);
        Function<Integer, String> func = value -> {
            throw new RuntimeException("Error");
        };

        EventStage<String> result = stage.map(func, executor);

        assertTrue(result instanceof DefaultEventPromise);
        verify(executor, times(1)).execute(any());
    }

    @Test
    public void testFlatMapWithExecutorInExecutor() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(true);

        SucceedEventStage<Integer> stage = new SucceedEventStage<>(10, executor);
        Function<Integer, EventStage<String>> func =
                value -> new SucceedEventStage<>("Value: " + value, executor);

        EventStage<String> result = stage.flatmap(func, executor);

        assertTrue(result instanceof SucceedEventStage<String>);
        assertEquals("Value: 10", ((SucceedEventStage<String>) result).value);
    }

    @Test
    public void testFlatMapWithExecutorNotInExecutor() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(false);

        SucceedEventStage<Integer> stage = new SucceedEventStage<>(10, executor);
        Function<Integer, EventStage<String>> func =
                value -> new SucceedEventStage<>("Value: " + value, executor);

        EventStage<String> result = stage.flatmap(func, executor);

        assertTrue(result instanceof DefaultEventPromise);
        verify(executor, times(1)).execute(any());
    }

    @Test
    public void testFlatMapWithExceptionInExecutor() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(true);

        SucceedEventStage<Integer> stage = new SucceedEventStage<>(10, executor);
        Function<Integer, EventStage<String>> func = value -> {
            throw new RuntimeException("Error");
        };

        EventStage<String> result = stage.flatmap(func, executor);

        assertTrue(result instanceof FailedEventFuture);
        assertEquals("Error", ((FailedEventFuture) result).cause.getMessage());
    }

    @Test
    public void testFlatMapWithExceptionNotInExecutor() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(false);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(executor).execute(any());

        SucceedEventStage<Integer> stage = new SucceedEventStage<>(10, executor);
        Function<Integer, EventStage<String>> func = value -> {
            throw new RuntimeException("Error");
        };

        EventStage<String> result = stage.flatmap(func, executor);
        assertTrue(result instanceof DefaultEventPromise);
        assertEquals("Error", result.toFuture().syncUninterruptibly().cause().getMessage());
    }
}
