package io.axor.commons.concurrent;

import java.util.concurrent.CompletableFuture;

/**
 * An abstract implementation of the {@code EventStageListener} interface that uses a
 * {@code CompletableFuture} to handle the success and failure of an event stage. This adaptor
 * provides a convenient way to work with asynchronous operations by completing the future based on
 * the outcome of the event stage.
 *
 * @param <T> the type of the value produced in case of success
 */
public class EventStageObserverAdaptor<T> implements EventStageObserver<T> {
    private final CompletableFuture<T> future;

    public EventStageObserverAdaptor(CompletableFuture<T> future) {
        this.future = future;
    }

    public EventStageObserverAdaptor() {
        this(new CompletableFuture<>());
    }

    @Override
    public final void success(T value) {
        future.complete(value);
    }

    @Override
    public final void failure(Throwable cause) {
        future.completeExceptionally(cause);
    }

    public CompletableFuture<T> getFuture() {
        return future;
    }
}
