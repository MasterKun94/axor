package io.axor.commons.stream;

import io.axor.commons.concurrent.DefaultSingleThreadEventExecutor;
import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.Try;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class EventStreamTest {
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
        EventStream<Integer> stream = newStream(10);
        assertEquals(executor, stream.executor());
        ElemCheckSubscriber subscriber = new ElemCheckSubscriber(IntStream.range(0, 10));
        stream.map(i -> {
                    assertEquals(executor, EventExecutor.currentExecutor());
                    return i;
                })
                .executor(executor2)
                .map(i -> {
                    assertEquals(executor2, EventExecutor.currentExecutor());
                    return i;
                })
                .subscribe(subscriber);
        subscriber.await();
    }

    @Test
    public void map() {
        ElemCheckSubscriber subscriber =
                new ElemCheckSubscriber(IntStream.range(0, 10).map(i -> i * 2));
        newStream(10).map(i -> i * 2)
                .subscribe(subscriber);
        subscriber.await();
    }

    @Test
    public void mapAsync() {
        SumCheckSubscriber subscriber = new SumCheckSubscriber(45, 10);
        newStream(10).mapAsync(i -> {
            EventPromise<Integer> promise = executor2.newPromise();
            executor2.schedule(() -> {
                promise.success(i);
            }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
            return promise;
        }).subscribe(subscriber);
        subscriber.await();

        subscriber = new SumCheckSubscriber(45, 10);
        newStream(10).mapAsync(i -> {
            EventPromise<Integer> promise = executor.newPromise();
            executor2.schedule(() -> {
                promise.success(i);
            }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
            return promise;
        }).subscribe(subscriber);
        subscriber.await();
    }

    @Test
    public void mapAsyncOrdered() {
        ElemCheckSubscriber subscriber = new ElemCheckSubscriber(IntStream.range(0, 10));
        newStream(10).mapAsyncOrdered(i -> {
            EventPromise<Integer> promise = executor2.newPromise();
            executor2.schedule(() -> {
                promise.success(i);
            }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
            return promise;
        }).subscribe(subscriber);
        subscriber.await();

        subscriber = new ElemCheckSubscriber(IntStream.range(0, 10));
        newStream(10).mapAsyncOrdered(i -> {
            EventPromise<Integer> promise = executor.newPromise();
            executor2.schedule(() -> {
                promise.success(i);
            }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
            return promise;
        }).subscribe(subscriber);
        subscriber.await();
    }

    @Test
    public void mapAsyncJuc() {
        SumCheckSubscriber subscriber = new SumCheckSubscriber(45, 10);
        newStream(10).mapAsyncJuc(i -> {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            executor2.schedule(() -> {
                future.complete(i);
            }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
            return future;
        }).subscribe(subscriber);
        subscriber.await();

        subscriber = new SumCheckSubscriber(45, 10);
        newStream(10).mapAsyncJuc(i -> {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            executor.schedule(() -> {
                future.complete(i);
            }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
            return future;
        }).subscribe(subscriber);
        subscriber.await();
    }

    @Test
    public void mapAsyncJucOrdered() {
        ElemCheckSubscriber subscriber = new ElemCheckSubscriber(IntStream.range(0, 10));
        newStream(10).mapAsyncJucOrdered(i -> {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            executor2.schedule(() -> {
                future.complete(i);
            }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
            return future;
        }).subscribe(subscriber);
        subscriber.await();

        subscriber = new ElemCheckSubscriber(IntStream.range(0, 10));
        newStream(10).mapAsyncJucOrdered(i -> {
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
        SumCheckSubscriber subscriber = new SumCheckSubscriber(45, 10);
        newStream(10).flatmapUnary(i -> {
            EventPromise<Integer> promise = executor2.newPromise();
            executor2.schedule(() -> {
                promise.success(i);
            }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
            return EventFlow.createUnary(promise);
        }).subscribe(subscriber);
        subscriber.await();

        subscriber = new SumCheckSubscriber(45, 10);
        newStream(10).flatmapUnary(i -> {
            EventPromise<Integer> promise = executor.newPromise();
            executor2.schedule(() -> {
                promise.success(i);
            }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
            return EventFlow.createUnary(promise);
        }).subscribe(subscriber);
        subscriber.await();
    }

    @Test
    public void flatmapStream() {
        TestSubscriber<Integer> subscriber = new ElemCheckSubscriber(IntStream.range(0, 100));
        newStream(10)
                .flatmapStream(i -> newStream(10, executor2).map(i0 -> i * 10 + i0))
                .subscribe(subscriber);
        subscriber.await();

        subscriber = new ElemCheckSubscriber(IntStream.range(0, 100));
        newStream(10)
                .flatmapStream(i -> newStream(10, executor).map(i0 -> i * 10 + i0))
                .subscribe(subscriber);
        subscriber.await();

        subscriber = new SumCheckSubscriber(4950, 100);
        newStream(10)
                .flatmapStream(i -> newStream(10, i % 2 == 1 ? executor : executor2).map(i0 -> i * 10 + i0))
                .subscribe(subscriber);
        subscriber.await();
    }

    @Test
    public void flatmapStreamOrdered() {
        ElemCheckSubscriber subscriber = new ElemCheckSubscriber(IntStream.range(0, 100));
        newStream(10)
                .flatmapStreamOrdered(i -> newStream(10, executor2).map(i0 -> i * 10 + i0))
                .subscribe(subscriber);
        subscriber.await();

        subscriber = new ElemCheckSubscriber(IntStream.range(0, 100));
        newStream(10)
                .flatmapStreamOrdered(i -> newStream(10, executor).map(i0 -> i * 10 + i0))
                .subscribe(subscriber);
        subscriber.await();

        subscriber = new ElemCheckSubscriber(IntStream.range(0, 100));
        newStream(10)
                .flatmapStreamOrdered(i -> newStream(10, i % 2 == 1 ? executor : executor2).map(i0 -> i * 10 + i0))
                .subscribe(subscriber);
        subscriber.await();
    }

    @Test
    public void flatmapUnaryOrdered() {
        ElemCheckSubscriber subscriber = new ElemCheckSubscriber(IntStream.range(0, 10));
        newStream(10).flatmapUnaryOrdered(i -> {
            EventPromise<Integer> promise = executor2.newPromise();
            executor2.schedule(() -> {
                promise.success(i);
            }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
            return EventFlow.createUnary(promise);
        }).subscribe(subscriber);
        subscriber.await();

        subscriber = new ElemCheckSubscriber(IntStream.range(0, 10));
        newStream(10).flatmapUnaryOrdered(i -> {
            EventPromise<Integer> promise = executor.newPromise();
            executor2.schedule(() -> {
                promise.success(i);
            }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
            return EventFlow.createUnary(promise);
        }).subscribe(subscriber);
        subscriber.await();
    }

    @Test
    public void filter() {
        var subscriber = new ElemCheckSubscriber(IntStream.range(0, 5).map(i -> i * 2));
        newStream(10).filter(i -> i % 2 == 0).subscribe(subscriber);
        subscriber.await();
    }

    @Test
    public void filterNot() {
        var subscriber = new ElemCheckSubscriber(IntStream.range(0, 5).map(i -> i * 2));
        newStream(10).filterNot(i -> i % 2 == 1).subscribe(subscriber);
        subscriber.await();
    }

    @Test
    public void reduce() {
        var future = newStream(10).reduce(Integer::sum).subscribe().toFuture();
        assertEquals(Try.success(45), future.syncUninterruptibly());
        var future0 = newStream(0).reduce(Integer::sum).subscribe().toFuture();
        Try<Integer> integerTry = future0.syncUninterruptibly();
        assertTrue(integerTry.isFailure());
        assertThrows(NoSuchElementException.class, () -> {
            throw Objects.requireNonNull(integerTry.cause());
        });
    }

    @Test
    public void reduceOption() {
        var future = newStream(10).reduceOption(Integer::sum).subscribe().toFuture();
        assertEquals(Try.success(Optional.of(45)), future.syncUninterruptibly());
        future = newStream(0).reduceOption(Integer::sum).subscribe().toFuture();
        assertEquals(Try.success(Optional.empty()), future.syncUninterruptibly());
    }

    @Test
    public void fold() {
        var future = newStream(10).fold(0, Integer::sum).subscribe().toFuture();
        assertEquals(Try.success(45), future.syncUninterruptibly());
        future = newStream(0).fold(0, Integer::sum).subscribe().toFuture();
        assertEquals(Try.success(0), future.syncUninterruptibly());
    }

    @Test
    public void withSubscription() {
        ElemCheckSubscriber subscriber = new ElemCheckSubscriber(IntStream.range(0, 10));
        ElemCheckSubscriber subscriber2 = new ElemCheckSubscriber(IntStream.range(0, 10));
        ElemCheckSubscriber subscriber3 = new ElemCheckSubscriber(IntStream.range(0, 10));
        ElemCheckSubscriber subscriber4 = new ElemCheckSubscriber(IntStream.range(0, 10));
        newStream(10)
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
        ElemCheckSubscriber subscriber =
                new ElemCheckSubscriber(IntStream.range(0, 5).map(i -> i * 2));
        newStream(10)
                .map(i -> i * 2)
                .mapAsyncJucOrdered(i -> {
                    CompletableFuture<Integer> future = new CompletableFuture<>();
                    executor2.schedule(() -> {
                        future.complete(i);
                    }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
                    return future;
                })
                .mapAsyncOrdered(i -> {
                    EventPromise<Integer> promise = executor2.newPromise();
                    executor2.schedule(() -> {
                        promise.success(i);
                    }, ThreadLocalRandom.current().nextInt(100), TimeUnit.MILLISECONDS);
                    return promise;
                })
                .subscribe(new LimitSubscriber(subscriber, 5));
        subscriber.await();

        subscriber = new ElemCheckSubscriber(IntStream.range(0, 5).map(i -> i * 2));
        var subscriber2 = new ElemCheckSubscriber(IntStream.range(0, 5));
        var subscriber3 = new ElemCheckSubscriber(IntStream.range(0, 5).map(i -> i * 2));
        newStream(10)
                .withSubscription(subscriber2)
                .map(i -> i * 2)
                .withSubscription(subscriber3)
                .subscribe(new LimitSubscriber(subscriber, 5));
        subscriber.await();
        subscriber2.await();
        subscriber3.await();

        subscriber = new ElemCheckSubscriber(IntStream.range(0, 5).map(i -> i * 2));
        subscriber2 = new ElemCheckSubscriber(IntStream.range(0, 5));
        subscriber3 = new ElemCheckSubscriber(IntStream.range(0, 5).map(i -> i * 2));
        newStream(10)
                .executor(executor2)
                .withSubscription(subscriber2)
                .map(i -> i * 2)
                .withSubscription(subscriber3)
                .subscribe(new LimitSubscriber(subscriber, 5));
        subscriber.await();
        subscriber2.await();
        subscriber3.await();
    }

    public static abstract class TestSubscriber<T> implements EventFlow.Subscriber<T> {
        private final CompletableFuture<Void> future = new CompletableFuture<>();

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

    public static class LimitSubscriber implements EventFlow.Subscriber<Integer> {
        private final EventFlow.Subscriber<Integer> delegate;
        private final int limit;
        private int count;

        public LimitSubscriber(EventFlow.Subscriber<Integer> delegate, int limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public void onEvent(Integer event) {
            count++;
            delegate.onEvent(event);
        }

        @Override
        public void onSignal(EventFlow.Signal signal) {
            delegate.onSignal(signal);
        }

        @Override
        public void onEnd() {
            delegate.onEnd();
            assertEquals(limit, count);
        }

        @Override
        public boolean continueFlag() {
            return count < limit;
        }
    }
}
