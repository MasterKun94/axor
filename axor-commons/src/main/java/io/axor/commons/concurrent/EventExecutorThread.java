package io.axor.commons.concurrent;

/**
 * A specialized {@link Thread} designed to work with an {@link EventExecutor}. This thread is aware
 * of the {@code EventExecutor} it is associated with, which allows for more sophisticated task
 * execution and management.
 */
public class EventExecutorThread extends Thread implements EventExecutor.ThreadWorker {
    private final EventExecutor executor;

    public EventExecutorThread(EventExecutor executor) {
        this.executor = executor;
    }

    public EventExecutorThread(EventExecutor executor, Runnable task) {
        super(task);
        this.executor = executor;
    }

    public EventExecutorThread(EventExecutor executor, Runnable task, String name) {
        super(task, name);
        this.executor = executor;
    }

    /**
     * Returns the {@code EventExecutor} that owns this thread.
     *
     * @return the owning {@code EventExecutor} instance
     */
    @Override
    public EventExecutor getOwnerExecutor() {
        return executor;
    }
}
