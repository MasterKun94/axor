package io.axor.commons.concurrent;

import java.util.Collection;
import java.util.Collections;

/**
 * An interface that represents a promise for an event, extending both {@link EventStage} and
 * {@link EventStageListener}. This interface allows for the creation of event promises that can be
 * listened to, canceled, and managed.
 *
 * <p>Implementations of this interface should provide the necessary functionality to handle the
 * lifecycle of an event, including success, failure, and cancellation. The interface provides
 * static factory methods to create new instances of {@code EventPromise} and a no-operation (noop)
 * instance.
 *
 * @param <T> the type of the result that the event promise will provide
 */
public non-sealed interface EventPromise<T> extends EventStage<T>, EventStageListener<T> {
    /**
     * Creates a new {@code EventPromise} with the specified {@code EventExecutor}.
     *
     * @param <T>      the type of the result that the event promise will provide
     * @param executor the {@code EventExecutor} to be used for executing the event promise
     * @return a new instance of {@code EventPromise} with the given executor
     */
    static <T> EventPromise<T> newPromise(EventExecutor executor) {
        return new DefaultEventPromise<>(executor);
    }

    /**
     * Creates a no-operation (noop) instance of {@code EventPromise} with the specified
     * {@code EventExecutor}.
     *
     * <p>This method returns a {@code NoopEventPromise}, which is a placeholder or stub for an
     * event promise. All operations on the returned promise are unsupported and will throw an
     * {@link UnsupportedOperationException}. This is useful in scenarios where a non-null
     * {@code EventPromise} is required but no actual asynchronous operation is intended to be
     * performed.
     *
     * @param <T>      the type of the result that the event promise would theoretically provide
     * @param executor the {@code EventExecutor} to be used for executing the event promise
     * @return a new instance of {@code NoopEventPromise} with the given executor
     */
    static <T> EventPromise<T> noop(EventExecutor executor) {
        return new NoopEventPromise<>(executor);
    }

    /**
     * Attempts to cancel the event promise. If the event is already in progress or has been
     * completed, this method may not have any effect.
     *
     * @return {@code true} if the event promise was successfully canceled, otherwise {@code false}
     */
    boolean cancel();

    /**
     * {@inheritDoc}
     */
    @Override
    default EventPromise<T> addListener(EventStageListener<T> listener) {
        return addListeners(Collections.singletonList(listener));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    EventPromise<T> addListeners(Collection<EventStageListener<T>> eventListeners);
}
