package io.axor.commons.concurrent;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Represents a future that is also an event stage, allowing for the execution of asynchronous tasks
 * and transformation of results. This interface extends both {@code EventStage} and {@code Future},
 * providing a way to handle asynchronous computations with event-driven capabilities.
 *
 * @param <T> the type of the result produced by the asynchronous computation
 */
public non-sealed interface EventFuture<T> extends EventStage<T>, Future<T> {

    /**
     * Creates a new {@code EventFuture} that is immediately completed with the given value.
     *
     * @param <T>      the type of the value that this future represents
     * @param value    the value with which the future is to be completed
     * @param executor the event executor to be used for running the completion
     * @return a new {@code EventFuture} that is already completed with the provided value
     */
    static <T> EventFuture<T> succeed(T value, EventExecutor executor) {
        return new SucceedEventFuture<>(value, executor);
    }

    /**
     * Creates a new {@code EventFuture} that is immediately completed exceptionally with the given
     * cause.
     *
     * @param <T>      the type of the value that this future represents
     * @param cause    the throwable representing the failure
     * @param executor the event executor to be used for running the completion
     * @return a new {@code EventFuture} that is already completed exceptionally with the provided
     * cause
     */
    static <T> EventFuture<T> failed(Throwable cause, EventExecutor executor) {
        return new FailedEventFuture<>(cause, executor);
    }

    /**
     * Schedules the provided callable to be executed asynchronously using the specified event
     * executor.
     *
     * @param <T>      the type of the result produced by the callable
     * @param callable the callable to be executed asynchronously
     * @param executor the event executor on which the callable will be executed
     * @return an {@code EventFuture} that will be completed with the result of the callable, or
     * exceptionally if an error occurs
     */
    static <T> EventFuture<T> supplyAsync(Callable<T> callable, EventExecutor executor) {
        return EventUtils.supplyAsync(callable, new DefaultEventFuture<>(executor));
    }

    /**
     * Schedules the provided callable to be executed asynchronously using the specified task
     * executor, and handles the completion of the future with the specified stage executor.
     *
     * @param <T>           the type of the result produced by the callable
     * @param callable      the callable to be executed asynchronously
     * @param taskExecutor  the executor on which the callable will be executed
     * @param stageExecutor the executor used for completing the future
     * @return an {@code EventFuture} that will be completed with the result of the callable, or
     * exceptionally if an error occurs
     */
    static <T> EventFuture<T> supplyAsync(Callable<T> callable, Executor taskExecutor,
                                          EventExecutor stageExecutor) {
        EventPromise<T> promise = stageExecutor.newFuturePromise();
        taskExecutor.execute(() -> {
            try {
                promise.success(callable.call());
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise.toFuture();
    }

    /**
     * Schedules the provided runnable to be executed asynchronously using the specified event
     * executor.
     *
     * @param runnable the runnable to be executed asynchronously
     * @param executor the event executor on which the runnable will be executed
     * @return an {@code EventFuture} that will be completed when the runnable completes, or
     * exceptionally if an error occurs
     */
    static EventFuture<Void> runAsync(Runnable runnable, EventExecutor executor) {
        return EventUtils.runAsync(runnable, new DefaultEventFuture<>(executor));
    }

    /**
     * Schedules the provided runnable to be executed asynchronously using the specified task
     * executor, and handles the completion of the future with the specified stage executor.
     *
     * @param runnable      the runnable to be executed asynchronously
     * @param taskExecutor  the executor on which the runnable will be executed
     * @param stageExecutor the executor used for completing the future
     * @return an {@code EventFuture} that will be completed when the runnable completes, or
     * exceptionally if an error occurs
     */
    static EventFuture<Void> runAsync(Runnable runnable, Executor taskExecutor,
                                      EventExecutor stageExecutor) {
        EventPromise<Void> promise = stageExecutor.newFuturePromise();
        taskExecutor.execute(() -> {
            try {
                runnable.run();
                promise.success(null);
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise.toFuture();
    }

    /**
     * Creates a new {@code EventPromise} with the specified {@code EventExecutor}.
     *
     * @param executor the {@code EventExecutor} to be used for executing the event promise
     * @return a new instance of {@code EventPromise} with the given executor
     */
    static <T> EventPromise<T> newPromise(EventExecutor executor) {
        return new DefaultEventFuture<>(executor);
    }

    EventExecutor executor();

    default <P> EventFuture<P> map(Function<T, P> func) {
        return map(func, executor());
    }

    <P> EventFuture<P> map(Function<T, P> func, EventExecutor executor);

    default <P> EventFuture<P> flatmap(Function<T, EventStage<P>> func) {
        return flatmap(func, executor());
    }

    <P> EventFuture<P> flatmap(Function<T, EventStage<P>> func, EventExecutor executor);

    @Override
    default <P> EventFuture<P> transform(Function<Try<T>, Try<P>> transformer) {
        return transform(transformer, executor());
    }

    @Override
    <P> EventFuture<P> transform(Function<Try<T>, Try<P>> transformer, EventExecutor executor);

    @Override
    default <P> EventFuture<P> flatTransform(Function<Try<T>, EventStage<P>> transformer) {
        return flatTransform(transformer, executor());
    }

    @Override
    <P> EventFuture<P> flatTransform(Function<Try<T>, EventStage<P>> transformer,
                                     EventExecutor executor);

    @Override
    EventFuture<T> observe(Collection<EventStageObserver<T>> observers);

    @Override
    default EventFuture<T> toFuture() {
        return this;
    }

    /**
     * Waits for the computation to complete and returns a {@code Try} instance that represents the
     * result of the computation. If the computation completes successfully, a {@code Success}
     * instance is returned. If the computation fails, a {@code Failure} instance is returned. This
     * method will block until the computation is complete or the thread is interrupted.
     *
     * @return a {@code Try} instance containing the result of the computation
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    Try<T> syncResult() throws InterruptedException;

    /**
     * Waits for the computation to complete and returns a {@code Try} instance that represents the
     * result of the computation. If the computation completes successfully, a {@code Success}
     * instance is returned. If the computation fails, a {@code Failure} instance is returned. This
     * method will block until the computation is complete, the specified timeout has elapsed, or
     * the thread is interrupted.
     *
     * @param time the maximum time to wait for the computation to complete
     * @param unit the time unit of the timeout argument
     * @return a {@code Try} instance containing the result of the computation
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    Try<T> syncResult(long time, TimeUnit unit) throws InterruptedException;

    /**
     * Waits for the computation to complete and returns a {@code Try} instance that represents the
     * result of the computation. If the computation completes successfully, a {@code Success}
     * instance is returned. If the computation fails, a {@code Failure} instance is returned. This
     * method will block until the computation is complete, ignoring any interruptions to the
     * current thread.
     *
     * @return a {@code Try} instance containing the result of the computation
     */
    Try<T> syncUninterruptibly();

    /**
     * Waits for the computation to complete and returns a {@code Try} instance that represents the
     * result of the computation. If the computation completes successfully, a {@code Success}
     * instance is returned. If the computation fails, a {@code Failure} instance is returned. This
     * method will block until the computation is complete, the specified timeout has elapsed, or
     * the thread is interrupted. Unlike other sync methods, this method ignores interruptions to
     * the current thread.
     *
     * @param time the maximum time to wait for the computation to complete
     * @param unit the time unit of the timeout argument
     * @return a {@code Try} instance containing the result of the computation
     */
    Try<T> syncUninterruptibly(long time, TimeUnit unit);
}
