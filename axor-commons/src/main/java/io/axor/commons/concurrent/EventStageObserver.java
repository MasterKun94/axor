package io.axor.commons.concurrent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * An interface for listening to the completion of an event stage, either successfully or with a
 * failure. Implementations of this interface are used to handle the result of asynchronous
 * operations.
 *
 * @param <T> the type of the value produced in case of success
 */
public interface EventStageObserver<T> {
    static <T> EventStageObserver<T> create(BiConsumer<T, Throwable> handler) {
        return new EventStageObserver<>() {
            @Override
            public void success(T value) {
                handler.accept(value, null);
            }

            @Override
            public void failure(Throwable cause) {
                handler.accept(null, cause);
            }
        };
    }

    static <T> EventStageObserver<T> create(Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        return new EventStageObserver<>() {
            @Override
            public void success(T value) {
                onSuccess.accept(value);
            }

            @Override
            public void failure(Throwable cause) {
                onFailure.accept(cause);
            }
        };
    }

    /**
     * Notifies the listener that the event stage has completed successfully with the provided
     * value.
     *
     * @param value the value produced as a result of the successful event stage
     */
    void success(T value);

    /**
     * Notifies the listener that the event stage has failed with the provided cause.
     *
     * @param cause the throwable representing the cause of the failure
     */
    void failure(Throwable cause);
}
