package io.axor.commons.concurrent;

/**
 * A non-sealed interface that extends {@link EventExecutor} and is designed to execute events in a
 * single-threaded manner. This interface ensures that all tasks submitted to the executor are
 * executed by a single thread, which can be useful for scenarios where thread safety and sequential
 * execution are required.
 *
 * <p>Implementations of this interface must provide a single-threaded execution model, ensuring
 * that tasks are processed one at a time in the order they are submitted.
 */
public non-sealed interface SingleThreadEventExecutor extends EventExecutor {
    String threadName();
}
