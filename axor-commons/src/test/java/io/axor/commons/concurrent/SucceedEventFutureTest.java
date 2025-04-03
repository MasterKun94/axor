package io.axor.commons.concurrent;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class SucceedEventFutureTest {

    @Test
    public void testGetReturnsValue() throws Exception {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        String value = "testValue";
        SucceedEventFuture<String> future = new SucceedEventFuture<>(value, executor);
        assertEquals(value, future.get());
    }

    @Test
    public void testGetWithTimeoutReturnsValue() throws Exception {
        EventExecutor executor = mock(SingleThreadEventExecutor.class);
        String value = "testValue";
        SucceedEventFuture<String> future = new SucceedEventFuture<>(value, executor);
        assertEquals(value, future.get(10, TimeUnit.MILLISECONDS));
    }
}
