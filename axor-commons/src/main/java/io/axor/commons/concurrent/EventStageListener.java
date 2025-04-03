package io.axor.commons.concurrent;

/**
 * An interface for listening to the completion of an event stage, either successfully or with a
 * failure. Implementations of this interface are used to handle the result of asynchronous
 * operations.
 *
 * @param <T> the type of the value produced in case of success
 */
public interface EventStageListener<T> {
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
