package io.axor.commons.stream;

import io.axor.commons.concurrent.DefaultSingleThreadEventExecutor;
import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventFuture;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.Try;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class EventUnaryTest {
    private static EventExecutor executor;
    private static EventExecutor executor2;
    private static ExecutorService ec;

    @BeforeClass
    public static void setup() {
        executor = new DefaultSingleThreadEventExecutor();
        executor2 = new DefaultSingleThreadEventExecutor();
        ec = Executors.newScheduledThreadPool(3);
    }

    @AfterClass
    public static void teardown() {
        executor.shutdown();
        executor2.shutdown();
        ec = Executors.newScheduledThreadPool(3);
    }

    private EventUnary<Integer> newUnary(int value) {
        return newUnary(value, executor);
    }

    private EventUnary<Integer> newUnary(int value, EventExecutor executor) {
        return EventFlow.createUnary(() -> value, executor);
    }

    private EventStream<Integer> newStream(int size) {
        return newStream(size, executor);
    }

    private EventStream<Integer> newStream(int size, EventExecutor executor) {
        return EventFlow.createStream(subscriber -> {
            for (int i = 0; i < size && subscriber.continueFlag(); i++) {
                subscriber.onEvent(i);
            }
            subscriber.onEnd();
        }, executor);
    }

    @Test
    public void executor() {
        EventUnary<Integer> unary = newUnary(10);
        assertEquals(executor, unary.executor());
        EventFuture<Integer> future = unary.map(i -> {
                    assertEquals(executor, EventExecutor.currentExecutor());
                    return i;
                })
                .executor(executor2)
                .map(i -> {
                    assertEquals(executor2, EventExecutor.currentExecutor());
                    return i;
                })
                .subscribe()
                .toFuture();
        assertEquals(Try.success(10), future.syncUninterruptibly());
    }

    @Test
    public void map() {
        EventFuture<Integer> future = newUnary(10)
                .map(i -> i * 2)
                .subscribe()
                .toFuture();
        assertEquals(Try.success(20), future.syncUninterruptibly());
    }

    @Test
    public void mapAsync() {
        EventFuture<Integer> future = newUnary(10).mapAsync(i -> {
                    EventPromise<Integer> promise = executor2.newPromise();
                    executor2.schedule(() -> {
                        promise.success(i);
                    }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
                    return promise;
                })
                .subscribe()
                .toFuture();
        assertEquals(Try.success(10), future.syncUninterruptibly());

        future = newUnary(10).mapAsync(i -> {
                    EventPromise<Integer> promise = executor.newPromise();
                    executor2.schedule(() -> {
                        promise.success(i);
                    }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
                    return promise;
                })
                .subscribe()
                .toFuture();
        assertEquals(Try.success(10), future.syncUninterruptibly());
    }

    @Test
    public void mapAsyncJuc() {
        SumCheckSubscriber subscriber = new SumCheckSubscriber(10, 1);
        newUnary(10).mapAsyncJuc(i -> {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            executor2.schedule(() -> {
                future.complete(i);
            }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
            return future;
        }).subscribe(subscriber);
        subscriber.await();

        subscriber = new SumCheckSubscriber(10, 1);
        newUnary(10).mapAsyncJuc(i -> {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            executor.schedule(() -> {
                future.complete(i);
            }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
            return future;
        }).subscribe(subscriber);
        subscriber.await();
    }

    @Test
    public void flatmapUnary() {
        EventFuture<Integer> future = newUnary(10)
                .flatmapUnary(i -> {
                    EventPromise<Integer> promise = executor2.newPromise();
                    executor2.schedule(() -> {
                        promise.success(i);
                    }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
                    return EventFlow.createUnary(promise);
                })
                .subscribe()
                .toFuture();
        assertEquals(Try.success(10), future.syncUninterruptibly());

        future = newUnary(10)
                .flatmapUnary(i -> {
                    EventPromise<Integer> promise = executor.newPromise();
                    executor2.schedule(() -> {
                        promise.success(i);
                    }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
                    return EventFlow.createUnary(promise);
                })
                .subscribe()
                .toFuture();
        assertEquals(Try.success(10), future.syncUninterruptibly());
    }

    @Test
    public void flatmapStream() {
        TestSubscriber<Integer> subscriber = new ElemCheckSubscriber(IntStream.range(100, 110));
        newUnary(10)
                .flatmapStream(i -> newStream(10, executor2).map(i0 -> i * 10 + i0))
                .subscribe(subscriber);
        subscriber.await();

        subscriber = new ElemCheckSubscriber(IntStream.range(100, 110));
        newUnary(10)
                .flatmapStream(i -> newStream(10, executor).map(i0 -> i * 10 + i0))
                .subscribe(subscriber);
        subscriber.await();

        subscriber = new ElemCheckSubscriber(IntStream.range(100, 110));
        newUnary(10)
                .flatmapStream(i -> newStream(10, i % 2 == 1 ? executor : executor2).map(i0 -> i * 10 + i0))
                .subscribe(subscriber);
        subscriber.await();
    }

    @Test
    public void withSubscription() {
        ElemCheckSubscriber subscriber = new ElemCheckSubscriber(IntStream.range(0, 1));
        ElemCheckSubscriber subscriber2 = new ElemCheckSubscriber(IntStream.range(0, 1));
        ElemCheckSubscriber subscriber3 = new ElemCheckSubscriber(IntStream.range(0, 1));
        ElemCheckSubscriber subscriber4 = new ElemCheckSubscriber(IntStream.range(0, 1));
        newUnary(0)
                .withSubscription(subscriber)
                .executor(executor2)
                .withSubscription(subscriber2)
                .withSubscription(subscriber3)
                .subscribe(subscriber4);
        subscriber.await();
        subscriber2.await();
        subscriber3.await();
        subscriber4.await();

    }

    @Test
    public void subscribe() {
    }

    public static abstract class TestSubscriber<T> implements EventFlow.Subscriber<T> {
        private CompletableFuture<Void> future = new CompletableFuture<>();

        @Override
        public void onSignal(EventFlow.Signal signal) {
            if (signal instanceof ErrorSignal(var cause))
                future.completeExceptionally(cause);
        }

        @Override
        public void onEnd() {
            future.complete(null);
        }

        public void await() {
            future.join();
        }
    }

    public static class ElemCheckSubscriber extends TestSubscriber<Integer> {
        private final List<Integer> check;
        private int nextOff = 0;

        public ElemCheckSubscriber(List<Integer> check) {
            this.check = check;
        }

        public ElemCheckSubscriber(Integer... check) {
            this.check = Arrays.asList(check);
        }

        public ElemCheckSubscriber(IntStream check) {
            this.check = check.boxed().toList();
        }

        @Override
        public void onEvent(Integer event) {
            assertEquals(check.get(nextOff++), event);
        }

        @Override
        public void onEnd() {
            assertEquals(check.size(), nextOff);
            super.onEnd();
        }
    }

    public static class SumCheckSubscriber extends TestSubscriber<Integer> {
        private final int expectSum;
        private final int expectNum;
        private int sum;
        private int num;

        public SumCheckSubscriber(int expectSum, int expectNum) {
            this.expectSum = expectSum;
            this.expectNum = expectNum;
        }

        @Override
        public void onEvent(Integer event) {
            sum += event;
            num++;
        }

        @Override
        public void onEnd() {
            assertEquals(expectSum, sum);
            assertEquals(expectNum, num);
            super.onEnd();
        }
    }
}
