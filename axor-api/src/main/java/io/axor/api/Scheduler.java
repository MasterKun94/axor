package io.axor.api;

import io.axor.api.impl.ActorUnsafe;
import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventPromise;
import io.axor.runtime.Signal;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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


    /**
     * Schedules a message to be sent to an actor after a specified delay.
     *
     * @param msg    the message to be sent to the actor
     * @param ref    the ActorRef representing the recipient actor
     * @param sender the ActorRef representing the sender of the message, or null if there is no
     *               sender
     * @param delay  the time to wait before sending the message
     * @param unit   the time unit of the delay parameter
     * @return a ScheduledFuture representing the scheduled task, which can be used to cancel or
     * check execution status
     */
    default <T> ScheduledFuture<?> scheduleTell(T msg, ActorRef<T> ref, ActorRef<?> sender,
                                                long delay, TimeUnit unit) {
        return schedule(() -> ref.tell(msg, sender), delay, unit, null);
    }

    /**
     * Schedules a message to be sent to an actor after a specified delay. The message is generated
     * by the provided supplier and sent to the target actor reference. The sender actor reference
     * is used as the sender of the message.
     *
     * @param msgSupplier a Supplier that generates the message to be sent
     * @param ref         the ActorRef representing the target actor to which the message will be
     *                    sent
     * @param sender      the ActorRef representing the sender of the message, or null if there is
     *                    no sender
     * @param delay       the time to wait before sending the message
     * @param unit        the TimeUnit for the delay parameter
     * @return a ScheduledFuture representing the scheduled task, which can be used to cancel or
     * check execution status
     */
    default <T> ScheduledFuture<?> scheduleTell(Supplier<T> msgSupplier, ActorRef<T> ref,
                                                ActorRef<?> sender, long delay, TimeUnit unit) {
        return schedule(() -> ref.tell(msgSupplier.get(), sender), delay, unit, null);
    }

    /**
     * Schedules a signal to be sent to the specified actor reference after a given delay.
     *
     * @param signal the signal to be scheduled for sending
     * @param ref    the actor reference to which the signal will be sent
     * @param delay  the time duration after which the signal will be sent
     * @param unit   the time unit of the delay parameter
     * @return a ScheduledFuture representing the scheduled task, which can be used to check
     * execution status or cancel the task
     */
    default ScheduledFuture<?> scheduleSignal(Signal signal, ActorRef<?> ref, long delay,
                                              TimeUnit unit) {
        return schedule(() -> ActorUnsafe.signal(ref, signal), delay, unit, null);
    }

    /**
     * Schedules a signal to be sent to the specified actor reference after a given delay.
     *
     * @param signalSupplier a supplier that provides the signal to be sent
     * @param ref            the actor reference to which the signal will be sent
     * @param delay          the time to wait before sending the signal
     * @param unit           the time unit of the delay parameter
     * @return a ScheduledFuture representing the scheduled task, which can be used to check the
     * status or cancel the task
     */
    default ScheduledFuture<?> scheduleSignal(Supplier<Signal> signalSupplier, ActorRef<?> ref,
                                              long delay, TimeUnit unit) {
        return schedule(() -> ActorUnsafe.signal(ref, signalSupplier.get()), delay, unit, null);
    }

    /**
     * Schedules a message to be sent to an actor at a fixed rate. The first message is sent after
     * the specified initial delay, and subsequent messages are sent periodically based on the given
     * period. The task continues until it is explicitly canceled or an exception occurs during
     * execution.
     *
     * @param msg          the message to be sent to the actor
     * @param ref          the recipient actor reference to which the message will be sent
     * @param sender       the sender actor reference, which may be null if there is no explicit
     *                     sender
     * @param initialDelay the time to delay the first message sending
     * @param period       the interval between successive message send operations
     * @param unit         the time unit for the initial delay and period
     * @return a ScheduledFuture representing the scheduled task, which can be used to cancel or
     * check execution status
     */
    default <T> ScheduledFuture<?> scheduleTellAtFixedRate(T msg, ActorRef<T> ref,
                                                           ActorRef<?> sender, long initialDelay,
                                                           long period, TimeUnit unit) {
        return scheduleAtFixedRate(() -> ref.tell(msg, sender), initialDelay, period, unit, null);
    }

    /**
     * Schedules a message to be sent at a fixed rate to the specified actor reference. The first
     * message is sent after the initial delay, and subsequent messages are sent periodically based
     * on the given period. The message is created using the provided message supplier each time it
     * is sent.
     *
     * @param msgSupplier  a supplier that generates the message to be sent
     * @param ref          the actor reference to which the message will be sent
     * @param sender       the sender actor reference associated with the message
     * @param initialDelay the time to delay first execution of the message sending
     * @param period       the period between successive executions of the message sending
     * @param unit         the time unit for the initial delay and period
     * @return a ScheduledFuture representing pending completion of the task, which can be used to
     * cancel or check execution status
     */
    default <T> ScheduledFuture<?> scheduleTellAtFixedRate(Supplier<T> msgSupplier,
                                                           ActorRef<T> ref, ActorRef<?> sender,
                                                           long initialDelay, long period,
                                                           TimeUnit unit) {
        return scheduleAtFixedRate(() -> ref.tell(msgSupplier.get(), sender), initialDelay,
                period, unit, null);
    }

    /**
     * Schedules a signal to be sent to the specified actor reference at a fixed rate. The task is
     * executed after the given initial delay and then repeatedly with the specified period until
     * cancelled or the scheduler terminates.
     *
     * @param signal       the signal to be sent to the actor
     * @param ref          the actor reference to which the signal will be sent
     * @param initialDelay the time to delay first execution of the task
     * @param period       the period between successive executions of the task
     * @param unit         the time unit of the initialDelay and period parameters
     * @return a ScheduledFuture representing the scheduled task, which can be used to cancel or
     * check execution status
     */
    default ScheduledFuture<?> scheduleSignalAtFixedRate(Signal signal, ActorRef<?> ref,
                                                         long initialDelay, long period,
                                                         TimeUnit unit) {
        return scheduleAtFixedRate(() -> ActorUnsafe.signal(ref, signal), initialDelay, period,
                unit, null);
    }

    /**
     * Schedules a signal to be sent to the specified actor at a fixed rate. The first signal is
     * sent after the specified initial delay, and subsequent signals are sent periodically based on
     * the given period. The task continues until cancelled or terminated by the scheduler.
     *
     * @param signalSupplier a supplier that provides the signal to be sent to the actor
     * @param ref            the actor reference to which the signal will be sent
     * @param initialDelay   the time to delay first execution of the task
     * @param period         the period between successive executions of the task
     * @param unit           the time unit for the initial delay and period
     * @return a ScheduledFuture representing the scheduled task, which can be used to cancel or
     * check execution status
     */
    default ScheduledFuture<?> scheduleSignalAtFixedRate(Supplier<Signal> signalSupplier,
                                                         ActorRef<?> ref, long initialDelay,
                                                         long period, TimeUnit unit) {
        return scheduleAtFixedRate(() -> ActorUnsafe.signal(ref, signalSupplier.get()),
                initialDelay, period, unit, null);
    }

    /**
     * Schedules a message to be sent to an actor with a fixed delay between subsequent executions.
     * The first message is sent after the specified initial delay, and subsequent messages are sent
     * after the specified period has elapsed from the completion of the previous execution.
     *
     * @param msg          the message to be sent to the actor
     * @param ref          the ActorRef representing the recipient actor
     * @param sender       the ActorRef representing the sender of the message, or null if there is
     *                     no sender
     * @param initialDelay the time to delay first execution of sending the message
     * @param period       the delay between the end of one execution and the start of the next
     * @param unit         the time unit of the initialDelay and period parameters
     * @return a ScheduledFuture representing pending completion of the task, which can be used to
     * cancel or check execution status
     */
    default <T> ScheduledFuture<?> scheduleTellWithFixedDelay(T msg, ActorRef<T> ref,
                                                              ActorRef<?> sender,
                                                              long initialDelay, long period,
                                                              TimeUnit unit) {
        return scheduleWithFixedDelay(() -> ref.tell(msg, sender), initialDelay, period, unit,
                null);
    }

    /**
     * Schedules a message to be sent to an actor with a fixed delay between subsequent executions.
     * The first message is sent after the specified initial delay, and subsequent messages are sent
     * after the specified period has elapsed from the completion of the previous send.
     *
     * @param msgSupplier  a supplier that provides the message to be sent to the actor
     * @param ref          the recipient actor reference to which the message will be sent
     * @param sender       the sender actor reference, or null if there is no specific sender
     * @param initialDelay the time to delay the first message send
     * @param period       the delay between the end of one message send and the start of the next
     * @param unit         the time unit for the initial delay and period
     * @return a ScheduledFuture representing the scheduled task, which can be used to cancel or
     * check execution status
     */
    default <T> ScheduledFuture<?> scheduleTellWithFixedDelay(Supplier<T> msgSupplier,
                                                              ActorRef<T> ref, ActorRef<?> sender
            , long initialDelay, long period, TimeUnit unit) {
        return scheduleWithFixedDelay(() -> ref.tell(msgSupplier.get(), sender), initialDelay,
                period, unit, null);
    }

    /**
     * Schedules a signal to be sent to the specified actor reference with a fixed delay between
     * subsequent executions. The first execution will occur after the initial delay, and subsequent
     * executions will occur after the specified period. If any execution encounters an exception,
     * subsequent executions will continue with the fixed delay.
     *
     * @param signal       the signal to be sent to the actor
     * @param ref          the actor reference that will receive the signal
     * @param initialDelay the time to delay the first execution
     * @param period       the delay between subsequent executions
     * @param unit         the time unit for the initial delay and period
     * @return a ScheduledFuture representing the scheduled task, which can be used to check the
     * status or cancel the task
     */
    default ScheduledFuture<?> scheduleSignalWithFixedDelay(Signal signal, ActorRef<?> ref,
                                                            long initialDelay, long period,
                                                            TimeUnit unit) {
        return scheduleWithFixedDelay(() -> ActorUnsafe.signal(ref, signal), initialDelay, period
                , unit, null);
    }

    /**
     * Schedules a signal to be sent to the specified actor reference with a fixed delay between
     * subsequent executions. The first execution occurs after the specified initial delay, and
     * subsequent executions are delayed by the given period.
     *
     * @param signalSupplier a supplier that provides the signal to be sent to the actor
     * @param ref            the actor reference to which the signal will be sent
     * @param initialDelay   the time to delay the first execution of the signal
     * @param period         the delay between subsequent executions of the signal
     * @param unit           the time unit for the initial delay and period
     * @return a ScheduledFuture representing the scheduled task, which can be used to check the
     * status or cancel the task
     */
    default ScheduledFuture<?> scheduleSignalWithFixedDelay(Supplier<Signal> signalSupplier,
                                                            ActorRef<?> ref, long initialDelay,
                                                            long period, TimeUnit unit) {
        return scheduleWithFixedDelay(() -> ActorUnsafe.signal(ref, signalSupplier.get()),
                initialDelay, period, unit, null);
    }
}
