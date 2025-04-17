package io.axor.commons.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * A specialized implementation of {@link EventFuture} that represents a future which has already
 * succeeded. This class extends {@link SucceedEventStage} and implements the {@link EventFuture}
 * interface, providing a future that is immediately available with a predefined value.
 *
 * @param <T> The type of the value held by this future.
 */
public final class SucceedEventFuture<T> extends SucceedEventStage<T> implements EventFuture<T> {

    public SucceedEventFuture(T value, EventExecutor executor) {
        super(value, executor);
    }

    @Override
    protected <P> EventPromise<P> newPromise(EventExecutor executor) {
        return new DefaultEventFuture<>(executor);
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return value;
    }

    @Override
    public T get(long timeout, @NotNull TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        return value;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public <P> EventFuture<P> map(Function<T, P> func, EventExecutor executor) {
        return super.map(func, executor).toFuture();
    }

    @Override
    public <P> EventFuture<P> flatmap(Function<T, EventStage<P>> func, EventExecutor executor) {
        return super.flatmap(func, executor).toFuture();
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
    public EventFuture<T> observe(Collection<EventStageObserver<T>> observers) {
        return super.observe(observers).toFuture();
    }

    @Override
    public Try<T> syncResult() {
        return Try.success(value);
    }

    @Override
    public Try<T> syncResult(long time, TimeUnit unit) {
        return Try.success(value);
    }

    @Override
    public Try<T> syncUninterruptibly() {
        return Try.success(value);
    }

    @Override
    public Try<T> syncUninterruptibly(long time, TimeUnit unit) {
        return Try.success(value);
    }
}
