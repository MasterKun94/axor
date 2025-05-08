package io.axor.runtime.scheduler;

import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventPromise;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * An interface representing a scheduler capable of managing and executing tasks with precise timing
 * and timeout constraints. It provides methods to schedule tasks, apply timeouts to futures or
 * promises, and execute tasks periodically with fixed rates or delays.
 * <p>
 * The scheduler is designed to work with {@link CompletableFuture}, {@link EventPromise}, and
 * {@link Runnable} tasks, allowing for flexible task management in asynchronous or concurrent
 * environments. It supports both one-time and periodic task scheduling, as well as the ability to
 * cancel tasks if they exceed specified timeouts.
 * <p>
 * The scheduler is associated with an {@link EventExecutor}, which is responsible for executing
 * scheduled tasks. Tasks can optionally be executed using a provided {@link ExecutorService},
 * allowing for integration with external thread pools or execution contexts.
 * <p>
 * Key functionalities include: - Applying timeouts to futures and promises to ensure timely
 * completion or cancellation. - Scheduling one-time tasks with a specified delay. - Scheduling
 * periodic tasks with either a fixed rate or a fixed delay between executions. - Integration with
 * external executors for task execution flexibility.
 */
public interface Scheduler {
    /**
     * Sets a timeout on the given {@link CompletableFuture}. If the future does not complete before
     * the specified timeout, it will be cancelled.
     *
     * @param future  the {@link CompletableFuture} to which the timeout will be applied
     * @param timeout the maximum time to wait for the future to complete
     * @param unit    the time unit of the timeout parameter
     * @return a new {@link CompletableFuture} that completes when the original future completes or
     * is cancelled due to the timeout
     */
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

    /**
     * Sets a timeout on the given {@link EventPromise}. If the promise does not complete before the
     * specified timeout, it will be canceled.
     *
     * @param promise the {@link EventPromise} to which the timeout will be applied
     * @param timeout the maximum time to wait for the promise to complete
     * @param unit    the time unit of the timeout parameter
     * @return a new {@link EventPromise} that completes when the original promise completes or is
     * canceled due to the timeout
     */
    default <T> EventPromise<T> setTimeout(EventPromise<T> promise, long timeout, TimeUnit unit) {
        if (promise.isDone()) {
            return promise;
        }
        ScheduledFuture<?> schedule = schedule(promise::cancel, timeout, unit, null);
        return (EventPromise<T>) promise.observe((t, e) -> {
            if (!schedule.isCancelled()) {
                schedule.cancel(false);
            }
        });
    }

    /**
     * Returns the {@link EventExecutor} associated with this scheduler.
     *
     * @return the {@link EventExecutor} instance used by this scheduler for executing events and
     * tasks
     */
    EventExecutor executor();

    /**
     * Schedules a task to be executed after a specified delay.
     *
     * @param command the task to execute, represented as a {@link Runnable}
     * @param delay   the time from now to delay execution
     * @param unit    the time unit of the delay parameter
     * @return a {@link ScheduledFuture} representing the scheduled task, which can be used to check
     * the status of the task or cancel it
     */
    default ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return schedule(command, delay, unit, executor());
    }

    /**
     * Schedules a task to be executed after a specified delay using the provided executor service.
     *
     * @param command  the task to execute, represented as a {@link Runnable}
     * @param delay    the time from now to delay execution
     * @param unit     the time unit of the delay parameter
     * @param executor the {@link ExecutorService} to use for executing the task
     * @return a {@link ScheduledFuture} representing the scheduled task, which can be used to check
     * the status of the task or cancel it
     */
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit,
                                ExecutorService executor);

    /**
     * Schedules a task to be executed after a specified delay. The task is represented as a
     * {@link Callable} and will be executed using the default executor associated with this
     * scheduler.
     *
     * @param callable the task to execute, represented as a {@link Callable} that produces a
     *                 result
     * @param delay    the time from now to delay execution
     * @param unit     the time unit of the delay parameter
     * @return a {@link ScheduledFuture} representing the scheduled task, which can be used to check
     * the status of the task, retrieve its result, or cancel it
     */
    default <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return schedule(callable, delay, unit, executor());
    }

    /**
     * Schedules a task to be executed after a specified delay using the provided executor service.
     * The task is represented as a {@link Callable} that produces a result.
     *
     * @param callable the task to execute, represented as a {@link Callable} that produces a
     *                 result
     * @param delay    the time from now to delay execution
     * @param unit     the time unit of the delay parameter
     * @param executor the {@link ExecutorService} to use for executing the task
     * @return a {@link ScheduledFuture} representing the scheduled task, which can be used to check
     * the status of the task, retrieve its result, or cancel it
     */
    <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit,
                                    ExecutorService executor);

    /**
     * Schedules a task to be executed periodically at a fixed rate. The task is executed after the
     * specified initial delay, and subsequent executions occur at the given period. If the task
     * takes longer to execute than the specified period, subsequent executions may start late but
     * will not overlap. The task is executed using the default executor associated with this
     * scheduler.
     *
     * @param command      the task to execute, represented as a {@link Runnable}
     * @param initialDelay the time to delay first execution
     * @param period       the period between successive executions
     * @param unit         the time unit of the initialDelay and period parameters
     * @return a {@link ScheduledFuture} representing the scheduled task, which can be used to check
     * the status of the task or cancel it
     */
    default ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay,
                                                   long period, TimeUnit unit) {
        return scheduleAtFixedRate(command, initialDelay, period, unit, executor());
    }

    /**
     * Schedules a task to be executed periodically at a fixed rate using the provided executor
     * service. The task is executed after the specified initial delay, and subsequent executions
     * occur at the given period. If the task takes longer to execute than the specified period,
     * subsequent executions may start late but will not overlap.
     *
     * @param command      the task to execute, represented as a {@link Runnable}
     * @param initialDelay the time to delay first execution
     * @param period       the period between successive executions
     * @param unit         the time unit of the initialDelay and period parameters
     * @param executor     the {@link ExecutorService} to use for executing the task
     * @return a {@link ScheduledFuture} representing the scheduled task, which can be used to check
     * the status of the task or cancel it
     */
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                                           TimeUnit unit, ExecutorService executor);

    /**
     * Schedules a task to be executed periodically with a fixed delay between the end of one
     * execution and the start of the next. The task is executed for the first time after the
     * specified initial delay, and subsequent executions occur after the completion of the previous
     * execution plus the specified delay. The task is executed using the default executor
     * associated with this scheduler.
     *
     * @param command      the task to execute, represented as a {@link Runnable}
     * @param initialDelay the time to delay first execution
     * @param period       the delay between the end of one execution and the start of the next
     * @param unit         the time unit of the initialDelay and period parameters
     * @return a {@link ScheduledFuture} representing the scheduled task, which can be used to check
     * the status of the task or cancel it
     */
    default ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
                                                      long period, TimeUnit unit) {
        return scheduleWithFixedDelay(command, initialDelay, period, unit, executor());
    }

    /**
     * Schedules a task to be executed periodically with a fixed delay between the end of one
     * execution and the start of the next. The task is executed for the first time after the
     * specified initial delay, and subsequent executions occur after the completion of the previous
     * execution plus the specified delay. The task is executed using the provided executor
     * service.
     *
     * @param command      the task to execute, represented as a {@link Runnable}
     * @param initialDelay the time to delay first execution
     * @param delay        the delay between the end of one execution and the start of the next
     * @param unit         the time unit of the initialDelay and delay parameters
     * @param executor     the {@link ExecutorService} to use for executing the task
     * @return a {@link ScheduledFuture} representing the scheduled task, which can be used to check
     * the status of the task or cancel it
     */
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                                              TimeUnit unit, ExecutorService executor);
}
