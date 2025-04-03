package io.axor.commons.concurrent;

import org.jetbrains.annotations.NotNull;

/**
 * A factory interface for creating instances of {@link EventExecutorThread}. Implementations of
 * this interface are responsible for providing new threads that are specifically designed to work
 * with an {@link EventExecutor}. The created threads will be used by the event executor to execute
 * tasks.
 *
 * <p>Implementations should ensure that the returned threads are properly configured and ready to
 * run. The factory may customize the thread properties such as name, priority, or any other
 * attributes as needed.
 *
 * @see EventExecutorThread
 */
public interface EventExecutorThreadFactory {
    /**
     * Creates a new instance of {@link EventExecutorThread} that is associated with the given
     * {@code EventExecutor} and will execute the provided {@code Runnable} task.
     *
     * @param executor the {@code EventExecutor} to which the thread will be associated
     * @param r        the {@code Runnable} task to be executed by the thread
     * @return a new instance of {@code EventExecutorThread} configured to run the specified task
     */
    EventExecutorThread newThread(EventExecutor executor, @NotNull Runnable r);
}
