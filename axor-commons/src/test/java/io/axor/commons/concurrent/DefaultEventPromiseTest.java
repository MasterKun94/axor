package io.axor.commons.concurrent;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DefaultEventPromiseTest {

    @Test
    public void testSuccessWithExecutorInExecutor() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(true);

        DefaultEventPromise<String> promise = new DefaultEventPromise<>(executor);
        promise.success("value");

        assertEquals(1, promise.status);
        assertEquals("value", promise.obj);
    }

    @Test
    public void testSuccessWithExecutorNotInExecutor() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(false);

        DefaultEventPromise<String> promise = new DefaultEventPromise<>(executor);
        promise.success("value");

        verify(executor).execute(any(Runnable.class));
    }

    @Test
    public void testDoSuccessWithListeners() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(true);

        EventStageObserver<String> listener = mock(EventStageObserver.class);
        DefaultEventPromise<String> promise = new DefaultEventPromise<>(executor);
        promise.listeners = Collections.singletonList(listener);

        promise.success("value");

        assertEquals(1, promise.status);
        assertEquals("value", promise.obj);
        verify(listener).success("value");
    }

    @Test
    public void testDoSuccessWithoutListeners() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(true);

        DefaultEventPromise<String> promise = new DefaultEventPromise<>(executor);
        promise.listeners = null;

        promise.success("value");

        assertEquals(1, promise.status);
        assertEquals("value", promise.obj);
    }

    @Test
    public void testSuccessMethodWithExistingListeners() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(true);

        EventStageObserver<String> listener = mock(EventStageObserver.class);
        DefaultEventPromise<String> promise = new DefaultEventPromise<>(executor);
        promise.listeners = Collections.singletonList(listener);

        promise.success("value");

        assertEquals(1, promise.status);
        assertEquals("value", promise.obj);
        verify(listener).success("value");
    }

    @Test
    public void testSuccessMethodWithNoListeners() {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        when(executor.inExecutor()).thenReturn(true);

        DefaultEventPromise<String> promise = new DefaultEventPromise<>(executor);
        promise.listeners = null;

        promise.success("value");

        assertEquals(1, promise.status);
        assertEquals("value", promise.obj);
    }
}
