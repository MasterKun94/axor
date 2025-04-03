package io.axor.commons.concurrent;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EventExecutorTestKit {
    private final SingleThreadEventExecutor executor;

    public EventExecutorTestKit(SingleThreadEventExecutor executor) {
        this.executor = executor;
    }

    public void test() throws Exception {
        assertFalse(executor.inExecutor());
        executor.submit(() -> {
            assertTrue(executor.inExecutor());
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
