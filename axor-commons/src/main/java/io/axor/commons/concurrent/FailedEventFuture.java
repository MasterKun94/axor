package io.axor.commons.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * A specialized implementation of {@link EventFuture} that represents a future which has already
 * failed. This class extends {@link FailedEventStage} and implements the {@link EventFuture}
 * interface, providing methods to handle and transform the failed state of the future.
 *
 * @param <T> The type of the result that this future is expected to produce.
 */
public final class FailedEventFuture<T> extends FailedEventStage<T> implements EventFuture<T> {

    public FailedEventFuture(Throwable cause, EventExecutor executor) {
        super(cause, executor);
    }

    @Override
    protected <P> EventPromise<P> newPromise(EventExecutor executor) {
        return new DefaultEventFuture<>(executor);
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        throw new ExecutionException(cause);
    }

    @Override
    public T get(long timeout, @NotNull TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        throw new ExecutionException(cause);
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
    public EventFuture<T> addListeners(Collection<EventStageListener<T>> eventStageListeners) {
        return super.addListeners(eventStageListeners).toFuture();
    }

    @Override
    public Try<T> syncResult() {
        return Try.failure(cause);
    }

    @Override
    public Try<T> syncResult(long time, TimeUnit unit) {
        return Try.failure(cause);
    }

    @Override
    public Try<T> syncUninterruptibly() {
        return Try.failure(cause);
    }

    @Override
    public Try<T> syncUninterruptibly(long time, TimeUnit unit) {
        return Try.failure(cause);
    }
}
