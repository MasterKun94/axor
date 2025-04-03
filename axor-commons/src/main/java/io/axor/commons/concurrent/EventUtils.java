package io.axor.commons.concurrent;

import java.util.concurrent.Callable;

public class EventUtils {
    /**
     * Executes a {@link Runnable} asynchronously and associates its execution with the provided
     * {@code EventPromise}.
     *
     * <p>If the current thread is already executing within the context of the event executor, the
     * runnable will be executed immediately. Otherwise, it will be scheduled for execution on the
     * event executor.
     *
     * @param <P>      the type of the event promise that extends {@link EventPromise<Void>}
     * @param runnable the {@link Runnable} to be executed
     * @param promise  the {@code EventPromise} to associate with the asynchronous task
     * @return the provided {@code EventPromise} after scheduling the runnable
     */
    public static <P extends EventPromise<Void>> P runAsync(Runnable runnable, P promise) {
        if (promise.executor().inExecutor()) {
            doRunAsync(runnable, promise);
        } else {
            promise.executor().execute(() -> doRunAsync(runnable, promise));
        }
        return promise;
    }

    private static <P extends EventPromise<Void>> void doRunAsync(Runnable runnable, P promise) {
        if (promise.isCancelled()) {
            return;
        }
        try {
            runnable.run();
            promise.success(null);
        } catch (Throwable e) {
            promise.failure(e);
        }
    }

    /**
     * Executes a {@link Callable} asynchronously and associates its execution with the provided
     * {@code EventPromise}.
     *
     * <p>If the current thread is already executing within the context of the event executor, the
     * callable will be executed immediately. Otherwise, it will be scheduled for execution on the
     * event executor.
     *
     * @param <T>      the type of the result that the callable will provide
     * @param <P>      the type of the event promise that extends {@link EventPromise<T>}
     * @param callable the {@link Callable} to be executed
     * @param promise  the {@code EventPromise} to associate with the asynchronous task
     * @return the provided {@code EventPromise} after scheduling the callable
     */
    public static <T, P extends EventPromise<T>> P supplyAsync(Callable<T> callable, P promise) {
        if (promise.executor().inExecutor()) {
            doSupplyAsync(callable, promise);
        } else {
            promise.executor().execute(() -> doSupplyAsync(callable, promise));
        }
        return promise;
    }

    private static <T, P extends EventPromise<T>> void doSupplyAsync(Callable<T> callable,
                                                                     P promise) {
        if (promise.isCancelled()) {
            return;
        }
        try {
            promise.success(callable.call());
        } catch (Throwable e) {
            promise.failure(e);
        }
    }
}
