package io.axor.testkit;

import io.axor.runtime.EventDispatcher;
import io.axor.runtime.EventDispatcherGroup;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class EventExecutorTestKit {
    private final EventDispatcherGroup eventExecutorGroup;

    public EventExecutorTestKit(EventDispatcherGroup eventExecutorGroup) {
        this.eventExecutorGroup = eventExecutorGroup;
    }

    public void test() throws Exception {
        assertNull(EventDispatcher.current());
        EventDispatcher executor = eventExecutorGroup.nextDispatcher();
        assertFalse(executor.inExecutor());
        executor.submit(() -> {
            assertTrue(executor.inExecutor());
            assertSame(executor, EventDispatcher.current());
        }).get();

        var future1 = new CompletableFuture<>();
        executor.schedule(() -> {
            future1.complete(null);
        }, 100, TimeUnit.MILLISECONDS);
        assertFalse(future1.isDone());
        Thread.sleep(80);
        assertFalse(future1.isDone());
        Thread.sleep(40);
        assertTrue(future1.isDone());
        assertFalse(future1.isCompletedExceptionally());

        var future2 = new CompletableFuture<>();
        executor.timeout(future2, 100, TimeUnit.MILLISECONDS);
        Thread.sleep(80);
        assertFalse(future2.isDone());
        Thread.sleep(40);
        assertTrue(future2.isCompletedExceptionally());

        AtomicInteger counter = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(() -> {
                for (int j = 0; j < 100000; j++) {
                    executor.execute(counter::incrementAndGet);
                }
                latch.countDown();
            });
            thread.start();
        }
        latch.await();
        executor.submit(() -> {
        }).get();
        assertEquals(1000000, counter.get());
    }
}
