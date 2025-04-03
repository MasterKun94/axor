package io.axor.commons.concurrent;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class FailedEventFutureTest {

    @Test
    public void testGetThrowsExecutionException() throws Exception {
        EventExecutor executor = Mockito.mock(SingleThreadEventExecutor.class);
        Throwable cause = new RuntimeException("Test exception");
        FailedEventFuture<String> future = new FailedEventFuture<>(cause, executor);

        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertEquals(cause, exception.getCause());
    }

    @Test
    public void testGetWithTimeoutThrowsExecutionException() throws Exception {
        EventExecutor executor = Mockito.mock(SingleThreadEventExecutor.class);
        Throwable cause = new RuntimeException("Test exception");
        FailedEventFuture<String> future = new FailedEventFuture<>(cause, executor);

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> future.get(100, TimeUnit.MILLISECONDS));
        assertEquals(cause, exception.getCause());
    }
}
