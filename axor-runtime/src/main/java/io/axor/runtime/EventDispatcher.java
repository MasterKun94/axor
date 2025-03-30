package io.axor.runtime;

import io.masterkun.stateeasy.concurrent.SingleThreadEventExecutor;

import java.util.concurrent.ScheduledExecutorService;

/**
 * An interface that extends {@link ScheduledExecutorService} and provides additional methods for
 * executing tasks, handling timeouts, and checking if the current thread is within the executor.
 */
public interface EventDispatcher extends SingleThreadEventExecutor {

    /**
     * Returns the current {@link EventDispatcher} if the current thread is an instance of
     * {@link DispatcherThread}. If the current thread is not an instance of
     * {@link DispatcherThread}, returns null.
     *
     * @return the current {@link EventDispatcher} or null if the current thread is not an instance
     * of {@link DispatcherThread}
     */
    static EventDispatcher current() {
        return Thread.currentThread() instanceof DispatcherThread ext ? ext.getOwnerExecutor() :
                null;
    }

    /**
     * An interface that defines a contract for threads that are associated with an
     * {@link EventDispatcher}. Implementations of this interface must provide a way to retrieve the
     * {@link EventDispatcher} that the thread is associated with.
     */
    interface DispatcherThread extends ThreadWorker {
        /**
         * Returns the {@link EventDispatcher} that this thread is associated with.
         *
         * @return the {@link EventDispatcher} associated with this thread, or null if the current
         * thread is not an instance of {@link DispatcherThread}
         */
        @Override
        EventDispatcher getOwnerExecutor();
    }
}
