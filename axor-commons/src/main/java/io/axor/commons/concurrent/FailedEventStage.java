package io.axor.commons.concurrent;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Represents an {@link EventStage} that has already failed with a specific cause. This class is
 * sealed and can only be extended by the {@link FailedEventFuture} class.
 *
 * @param <T> The type of the result that this stage is expected to produce.
 */
public sealed class FailedEventStage<T> implements EventStage<T> permits FailedEventFuture {
    protected final Throwable cause;
    protected final EventExecutor executor;

    public FailedEventStage(Throwable cause, EventExecutor executor) {
        this.cause = cause;
        this.executor = executor;
    }

    protected <P> EventPromise<P> newPromise(EventExecutor executor) {
        return new DefaultEventPromise<>(executor);
    }

    @Override
    public boolean isDone() {
        return true;
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
        return true;
    }

    @Override
    public @Nullable Try<T> getResult() {
        return Try.failure(cause);
    }

    @Override
    public EventExecutor executor() {
        return executor;
    }

    @Override
    public EventStage<T> executor(EventExecutor executor) {
        return new FailedEventStage<>(cause, executor);
    }

    @Override
    public <P> EventStage<P> map(Function<T, P> func, EventExecutor executor) {
        return new FailedEventStage<>(cause, executor);
    }

    @Override
    public <P> EventStage<P> flatmap(Function<T, EventStage<P>> func, EventExecutor executor) {
        return new FailedEventStage<>(cause, executor);
    }

    @Override
    public <P> EventStage<P> transform(Function<Try<T>, Try<P>> transformer,
                                       EventExecutor executor) {
        if (executor.inExecutor()) {
            try {
                return switch (transformer.apply(Try.failure(cause))) {
                    case Success<P>(P v) -> new SucceedEventStage<>(v, executor);
                    case Failure<?>(Throwable e) -> new FailedEventStage<>(e, executor);
                };
            } catch (Throwable e) {
                return new FailedEventStage<>(e, executor);
            }
        } else {
            EventPromise<P> future = newPromise(executor);
            executor.execute(() -> {
                switch (transformer.apply(Try.failure(cause))) {
                    case Success<P>(P v) -> future.success(v);
                    case Failure<?>(Throwable e) -> future.failure(e);
                }
            });
            return future;
        }
    }

    @Override
    public <P> EventStage<P> flatTransform(Function<Try<T>, EventStage<P>> transformer,
                                           EventExecutor executor) {
        if (executor.inExecutor()) {
            try {
                return transformer.apply(Try.failure(cause));
            } catch (Throwable e) {
                return new FailedEventStage<>(e, executor);
            }
        } else {
            EventPromise<P> future = newPromise(executor);
            executor.execute(() -> {
                try {
                    transformer.apply(Try.failure(cause)).observe(future);
                } catch (Throwable e) {
                    future.failure(e);
                }
            });
            return future;
        }
    }

    @Override
    public EventStage<T> observe(Collection<EventStageObserver<T>> observers) {
        if (executor.inExecutor()) {
            for (EventStageObserver<T> listener : observers) {
                try {
                    listener.failure(cause);
                } catch (Throwable e) {
                    listener.failure(e);
                }
            }

        } else {
            executor.execute(() -> observe(observers));
        }
        return this;
    }

    @Override
    public EventStage<T> withTimeout(long timeout, TimeUnit unit) {
        return this;
    }
}
