package io.axor.commons.concurrent;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Function;

/**
 * A no-operation implementation of the {@link EventPromise} interface. This class is designed to
 * serve as a placeholder or stub for an event promise, where all operations are unsupported and
 * throw an {@link UnsupportedOperationException}. The primary use case for this class is in
 * scenarios where a non-null {@code EventPromise} is required but no actual asynchronous operation
 * is intended to be performed.
 *
 * <p>Methods that would typically indicate the state of the promise, such as {@link #isDone()},
 * {@link #isCancelled()}, {@link #isSuccess()}, and {@link #isFailure()}, always return
 * {@code false}. Methods that would normally trigger or transform the promise, such as
 * {@link #getResult()}, {@link #map(Function, EventExecutor)}, and
 * {@link #transform(Function, EventExecutor)}, throw an {@link UnsupportedOperationException}.
 *
 * @param <T> the type of the result that the promise would theoretically provide
 */
public class NoopEventPromise<T> implements EventPromise<T> {
    private final EventExecutor executor;

    public NoopEventPromise(EventExecutor executor) {
        this.executor = executor;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isSuccess() {
        return false;
    }

    @Override
    public boolean isFailure() {
        return false;
    }

    @Override
    public @Nullable Try<T> getResult() {
        throw new UnsupportedOperationException("NoopEventPromise is not support");
    }

    @Override
    public EventExecutor executor() {
        if (executor != null) {
            return executor;
        }
        throw new UnsupportedOperationException("NoopEventPromise is not support");
    }

    @Override
    public EventStage<T> executor(EventExecutor executor) {
        return new NoopEventPromise<>(executor);
    }

    @Override
    public <P> EventStage<P> map(Function<T, P> func, EventExecutor executor) {
        throw new UnsupportedOperationException("NoopEventPromise is not support");
    }

    @Override
    public <P> EventStage<P> flatmap(Function<T, EventStage<P>> func, EventExecutor executor) {
        throw new UnsupportedOperationException("NoopEventPromise is not support");
    }

    @Override
    public <P> EventStage<P> transform(Function<Try<T>, Try<P>> transformer,
                                       EventExecutor executor) {
        throw new UnsupportedOperationException("NoopEventPromise is not support");
    }

    @Override
    public <P> EventStage<P> flatTransform(Function<Try<T>, EventStage<P>> transformer,
                                           EventExecutor executor) {
        throw new UnsupportedOperationException("NoopEventPromise is not support");
    }

    @Override
    public boolean cancel() {
        return false;
    }

    @Override
    public EventPromise<T> observe(Collection<EventStageObserver<T>> observers) {
        return this;
    }

    @Override
    public void success(T value) {

    }

    @Override
    public void failure(Throwable cause) {

    }
}
