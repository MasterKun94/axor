package io.axor.commons.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * A default implementation of the EventFuture interface that extends DefaultEventPromise. This
 * class is designed to handle asynchronous events and their results, providing methods to manage
 * the lifecycle of an event, including success, failure, and cancellation. It uses a CountDownLatch
 * to synchronize on the completion of the event.
 *
 * @param <T> The type of the result of the event.
 */
class DefaultEventFuture<T> extends DefaultEventPromise<T> implements EventFuture<T> {
    private final CountDownLatch latch = new CountDownLatch(1);

    DefaultEventFuture(EventExecutor eventExecutor) {
        super(eventExecutor);
    }

    DefaultEventFuture(EventStage<T> stage) {
        super(stage.executor());
        stage.addListener(this);
    }

    @Override
    protected boolean doSuccess(T value) {
        if (super.doSuccess(value)) {
            latch.countDown();
            return true;
        }
        return false;
    }

    @Override
    protected boolean doFailed(Throwable cause) {
        if (super.doFailed(cause)) {
            latch.countDown();
            return true;
        }
        return false;
    }

    @Override
    protected <P> EventPromise<P> newPromise(EventExecutor executor) {
        return new DefaultEventFuture<>(executor);
    }

    @Override
    protected EventStage<T> toCompletedStage(EventExecutor executor) {
        assert getResult() != null;
        return switch (getResult()) {
            case Success<T>(T value) -> new SucceedEventFuture<>(value, executor);
            case Failure<T>(Throwable e) -> new FailedEventFuture<>(e, executor);
            case null -> throw new RuntimeException("should never happen");
        };
    }

    @Override
    public <P> EventFuture<P> map(Function<T, P> func) {
        return super.map(func).toFuture();
    }

    @Override
    public <P> EventFuture<P> map(Function<T, P> func, EventExecutor executor) {
        return super.map(func, executor).toFuture();
    }

    @Override
    public <P> EventFuture<P> flatmap(Function<T, EventStage<P>> func) {
        return super.flatmap(func).toFuture();
    }

    @Override
    public <P> EventFuture<P> flatmap(Function<T, EventStage<P>> func, EventExecutor executor) {
        return super.flatmap(func, executor).toFuture();
    }

    @Override
    public <P> EventFuture<P> transform(Function<Try<T>, Try<P>> transformer) {
        return super.transform(transformer).toFuture();
    }

    @Override
    public <P> EventFuture<P> transform(Function<Try<T>, Try<P>> transformer,
                                        EventExecutor executor) {
        return super.transform(transformer, executor).toFuture();
    }

    @Override
    public <P> EventFuture<P> flatTransform(Function<Try<T>, EventStage<P>> transformer,
                                            EventExecutor executor) {
        return super.flatTransform(transformer, executor).toFuture();
    }

    @Override
    public <P> EventFuture<P> flatTransform(Function<Try<T>, EventStage<P>> transformer) {
        return super.flatTransform(transformer).toFuture();
    }

    @Override
    public DefaultEventFuture<T> addListeners(Collection<EventStageListener<T>> eventListeners) {
        return (DefaultEventFuture<T>) super.addListeners(eventListeners);
    }

    @Override
    public DefaultEventFuture<T> addListener(EventStageListener<T> listener) {
        return (DefaultEventFuture<T>) super.addListener(listener);
    }

    @Override
    public Try<T> syncResult() throws InterruptedException {
        latch.await();
        return getResult();
    }

    @Override
    public Try<T> syncResult(long time, TimeUnit unit) throws InterruptedException {
        latch.await(time, unit);
        Try<T> res = getResult();
        return res == null ? Try.failure(new TimeoutException()) : res;
    }

    @Override
    public Try<T> syncUninterruptibly() {
        while (true) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                continue;
            }
            return getResult();
        }
    }

    @Override
    public Try<T> syncUninterruptibly(long time, TimeUnit unit) {
        long nanos = unit.toNanos(time);
        long current = System.currentTimeMillis();
        while (nanos > 0) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                nanos = System.currentTimeMillis() - current - nanos;
                continue;
            }
            return getResult();
        }
        return Try.failure(new TimeoutException());
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cancel();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return switch (syncResult()) {
            case Success<T>(T value) -> value;
            case Failure<?>(Throwable cause) -> {
                throw new ExecutionException(cause);
            }
        };
    }

    @Override
    public T get(long timeout, @NotNull TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        return switch (syncResult(timeout, unit)) {
            case Success<T>(T value) -> value;
            case Failure<?>(Throwable cause) -> {
                if (cause instanceof TimeoutException e) {
                    throw e;
                }
                throw new ExecutionException(cause);
            }
        };
    }
}
