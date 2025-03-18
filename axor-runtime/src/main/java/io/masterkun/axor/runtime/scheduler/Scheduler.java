package io.masterkun.axor.runtime.scheduler;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The Scheduler interface provides methods to schedule tasks for future execution. It supports
 * various scheduling options such as one-time, fixed-rate, and fixed-delay executions.
 */
public interface Scheduler {
    default <T> CompletableFuture<T> setTimeout(CompletableFuture<T> future, long timeout,
                                                TimeUnit unit) {
        if (future.isDone()) {
            return future;
        }
        ScheduledFuture<?> schedule = schedule(() -> future.cancel(false), timeout, unit, null);
        return future.whenComplete((t, e) -> {
            if (!schedule.isCancelled()) {
                schedule.cancel(false);
            }
        });
    }

    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit,
                                ExecutorService executor);

    <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit,
                                    ExecutorService executor);

    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                                           TimeUnit unit, ExecutorService executor);

    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                                              TimeUnit unit, ExecutorService executor);
}
