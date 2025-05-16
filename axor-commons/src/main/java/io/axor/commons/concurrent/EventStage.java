package io.axor.commons.concurrent;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents a stage in the processing of an asynchronous event. This interface provides methods to
 * manage and transform the outcome of an asynchronous computation, including success, failure, and
 * cancellation states.
 * <p>
 * The {@code EventStage} is designed to be used with an {@link EventExecutor}, which is responsible
 * for executing the asynchronous tasks. Implementations of this interface are sealed to ensure that
 * only specific types can implement it, such as {@link EventPromise} and {@link EventFuture}.
 *
 * @param <T> the type of the result of the asynchronous computation
 */
public sealed interface EventStage<T> permits EventPromise, EventFuture,
        SucceedEventStage, FailedEventStage {

    /**
     * Creates a new {@code EventStage} that is immediately completed with the given value.
     *
     * @param <T>      the type of the value
     * @param value    the value to be used for completing the stage
     * @param executor the executor to be used for running the completion
     * @return a new {@code EventStage} that is already completed with the provided value
     */
    static <T> EventStage<T> succeed(T value, EventExecutor executor) {
        return new SucceedEventStage<>(value, executor);
    }

    /**
     * Creates a new {@code EventStage} that represents a failure.
     *
     * @param <T>      the type of the result that this stage would have represented, had it not
     *                 failed
     * @param cause    the cause of the failure
     * @param executor the executor to use for executing any listeners attached to the returned
     *                 stage
     * @return a new {@code EventStage} representing the given failure
     */
    static <T> EventStage<T> failed(Throwable cause, EventExecutor executor) {
        return new FailedEventStage<>(cause, executor);
    }

    /**
     * Asynchronously executes a given {@code Callable} and returns an {@code EventStage} that will
     * be completed with the result of the callable.
     *
     * @param <T>      the type of the result produced by the callable
     * @param callable the {@code Callable} to be executed asynchronously
     * @param executor the {@code EventExecutor} on which the callable will be executed
     * @return an {@code EventStage} that will be completed with the result of the callable or
     * exceptionally if the callable throws an exception
     */
    static <T> EventStage<T> supplyAsync(Callable<T> callable, EventExecutor executor) {
        return EventUtils.supplyAsync(callable, new DefaultEventPromise<>(executor));
    }


    /**
     * Asynchronously executes a task using the provided callable and returns an EventStage that
     * will be completed with the result of the callable or a failure if an exception occurs.
     *
     * @param <T>           the type of the result produced by the callable
     * @param callable      the callable to be executed asynchronously
     * @param taskExecutor  the executor to run the callable
     * @param stageExecutor the executor to handle the completion of the promise
     * @return an EventStage that will be completed with the result of the callable or a failure
     */
    static <T> EventStage<T> supplyAsync(Callable<T> callable, Executor taskExecutor,
                                         EventExecutor stageExecutor) {
        EventPromise<T> promise = stageExecutor.newPromise();
        taskExecutor.execute(() -> {
            try {
                promise.success(callable.call());
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise;
    }

    /**
     * Asynchronously executes a given {@code Runnable} and returns an {@code EventStage} that will
     * be completed when the runnable finishes.
     *
     * @param runnable the {@code Runnable} to be executed asynchronously
     * @param executor the {@code EventExecutor} on which the runnable will be executed
     * @return an {@code EventStage} that will be completed when the runnable finishes, or
     * exceptionally if the runnable throws an exception
     */
    static EventStage<Void> runAsync(Runnable runnable, EventExecutor executor) {
        return EventUtils.runAsync(runnable, new DefaultEventPromise<>(executor));
    }

    /**
     * Executes a given {@code Runnable} asynchronously using the specified task executor and
     * associates its completion (either success or failure) with an event stage that is managed by
     * the provided stage executor.
     *
     * @param runnable      The task to be executed asynchronously.
     * @param taskExecutor  The executor that will run the given task.
     * @param stageExecutor the executor to handle the completion of the promise
     * @return An {@code EventStage<Void>} representing the completion of the asynchronous task,
     * which can be used to track when the task has completed, either successfully or with a
     * failure.
     */
    static EventStage<Void> runAsync(Runnable runnable, Executor taskExecutor,
                                     EventExecutor stageExecutor) {
        EventPromise<Void> promise = stageExecutor.newPromise();
        taskExecutor.execute(() -> {
            try {
                runnable.run();
                promise.success(null);
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise;
    }

    /**
     * Creates a new {@code EventPromise} with the specified {@code EventExecutor}.
     *
     * @param <T>      the type of the result produced by the promise
     * @param executor the {@code EventExecutor} on which the promise will be executed
     * @return a new {@code EventPromise} that can be completed or exceptionally completed
     */
    static <T> EventPromise<T> newPromise(EventExecutor executor) {
        return new DefaultEventPromise<>(executor);
    }

    /**
     * Creates a no-operation (noop) {@code EventPromise} with the specified {@code EventExecutor}.
     *
     * <p>This method returns an {@code EventPromise} that does not perform any actual asynchronous
     * operations. All methods on the returned promise will either return a fixed value or throw an
     * {@link UnsupportedOperationException}. This is useful in scenarios where a non-null
     * {@code EventPromise} is required, but no actual operation is intended.
     *
     * @param <T>      the type of the result produced by the promise
     * @param executor the {@code EventExecutor} on which the promise will be executed
     * @return a new noop {@code EventPromise} that does not perform any operations
     */
    static <T> EventPromise<T> noopPromise(EventExecutor executor) {
        return new NoopEventPromise<>(executor);
    }

    /**
     * Checks if this EventStage has completed, either successfully, by being cancelled, or
     * exceptionally.
     *
     * @return {@code true} if the EventStage has completed, otherwise {@code false}
     */
    boolean isDone();

    /**
     * Checks if this EventStage has been cancelled.
     *
     * @return {@code true} if the EventStage has been cancelled, otherwise {@code false}
     */
    boolean isCancelled();

    /**
     * Checks if this EventStage has completed successfully.
     *
     * @return {@code true} if the EventStage has completed successfully, otherwise {@code false}
     */
    boolean isSuccess();

    /**
     * Checks if this EventStage has completed exceptionally.
     *
     * @return {@code true} if the EventStage has completed with an exception, otherwise
     * {@code false}
     */
    boolean isFailure();

    /**
     * Retrieves the result of this {@code EventStage} if it has completed.
     *
     * <p>If the {@code EventStage} has not yet completed, this method will return {@code null}.
     * If the {@code EventStage} has completed successfully, it returns a {@code Try} containing the
     * result. If the {@code EventStage} has completed exceptionally, it returns a {@code Try}
     * containing the exception.
     *
     * @return a {@code Try} containing the result or the exception, or {@code null} if the
     * {@code EventStage} has not completed
     */
    @Nullable
    Try<T> getResult();

    /**
     * Returns the {@code EventExecutor} associated with this {@code EventStage}.
     *
     * @return the {@code EventExecutor} that is responsible for executing tasks for this
     * {@code EventStage}
     */
    EventExecutor executor();

    EventStage<T> executor(EventExecutor executor);

    /**
     * Applies a function to the result of this {@code EventStage} and returns a new
     * {@code EventStage} with the transformed result. The transformation is executed on the
     * {@code EventExecutor} associated with this {@code EventStage}.
     *
     * @param <P>  the type of the result produced by the function
     * @param func the function to apply to the result of this {@code EventStage}
     * @return a new {@code EventStage} that will be completed with the result of applying the
     * function to the result of this {@code EventStage}
     */
    default <P> EventStage<P> map(Function<T, P> func) {
        return map(func, executor());
    }

    /**
     * Applies a function to the result of this {@code EventStage} and returns a new
     * {@code EventStage} with the transformed result. The transformation is executed on the
     * specified {@code EventExecutor}.
     *
     * @param <P>      the type of the result produced by the function
     * @param func     the function to apply to the result of this {@code EventStage}
     * @param executor the {@code EventExecutor} on which the function will be executed
     * @return a new {@code EventStage} that will be completed with the result of applying the
     * function to the result of this {@code EventStage}
     */
    <P> EventStage<P> map(Function<T, P> func, EventExecutor executor);

    /**
     * Applies a function to the result of this {@code EventStage} and returns a new
     * {@code EventStage} with the transformed result. The transformation is executed on the
     * {@code EventExecutor} associated with this {@code EventStage}. The function provided should
     * return an {@code EventStage}.
     *
     * @param <P>  the type of the result produced by the function
     * @param func the function to apply to the result of this {@code EventStage}, which returns an
     *             {@code EventStage}
     * @return a new {@code EventStage} that will be completed with the result of the
     * {@code EventStage} returned by the function
     */
    default <P> EventStage<P> flatmap(Function<T, EventStage<P>> func) {
        return flatmap(func, executor());
    }

    /**
     * Applies a function to the result of this {@code EventStage} and returns a new
     * {@code EventStage} with the transformed result. The transformation is executed on the
     * specified {@code EventExecutor}. The function provided should return an {@code EventStage}.
     *
     * @param <P>      the type of the result produced by the function
     * @param func     the function to apply to the result of this {@code EventStage}, which returns
     *                 an {@code EventStage}
     * @param executor the {@code EventExecutor} on which the function will be executed
     * @return a new {@code EventStage} that will be completed with the result of the
     * {@code EventStage} returned by the function
     */
    <P> EventStage<P> flatmap(Function<T, EventStage<P>> func, EventExecutor executor);

    /**
     * Transforms the result of this {@code EventStage} using the provided transformer function and
     * returns a new {@code EventStage} with the transformed result. The transformation is executed
     * on the {@code EventExecutor} associated with this {@code EventStage}.
     *
     * @param <P>         the type of the result produced by the transformer function
     * @param transformer the function to apply to the result of this {@code EventStage}, which
     *                    takes a {@code Try<T>} and returns a {@code Try<P>}
     * @return a new {@code EventStage} that will be completed with the result of applying the
     * transformer function to the result of this {@code EventStage}
     */
    default <P> EventStage<P> transform(Function<Try<T>, Try<P>> transformer) {
        return transform(transformer, executor());
    }

    /**
     * Transforms the result of this {@code EventStage} using the provided transformer function and
     * returns a new {@code EventStage} with the transformed result. The transformation is executed
     * on the specified {@code EventExecutor}.
     *
     * @param <P>         the type of the result produced by the transformer function
     * @param transformer the function to apply to the result of this {@code EventStage}, which
     *                    takes a {@code Try<T>} and returns a {@code Try<P>}
     * @param executor    the {@code EventExecutor} on which the transformer function will be
     *                    executed
     * @return a new {@code EventStage} that will be completed with the result of applying the
     * transformer function to the result of this {@code EventStage}
     */
    <P> EventStage<P> transform(Function<Try<T>, Try<P>> transformer, EventExecutor executor);

    /**
     * Applies a transformation to each element of the current EventStage, where the transformation
     * function returns a new EventStage. The resulting stages are then flattened into a single
     * EventStage.
     *
     * @param transformer the function that takes a Try of the current EventStage's element type and
     *                    returns a new EventStage of a potentially different type
     * @param <P>         the type of elements in the resulting EventStage
     * @return a new EventStage containing the elements from the transformed and flattened stages
     */
    default <P> EventStage<P> flatTransform(Function<Try<T>, EventStage<P>> transformer) {
        return flatTransform(transformer, executor());
    }

    /**
     * Transforms the elements emitted by the current EventStage using a provided function, and
     * flattens the results into a single stream. The transformation is applied to each element
     * wrapped in a Try monad, allowing for error handling within the transformation process. The
     * resulting EventStage will emit items from the transformed streams, potentially on a different
     * executor if specified.
     *
     * @param transformer the function that transforms each element (wrapped in a Try) into an
     *                    EventStage of new elements
     * @param executor    the EventExecutor on which the transformed EventStages should be executed;
     *                    if null, the original executor is used
     * @param <P>         the type of the elements in the resulting EventStage
     * @return a new EventStage that emits the flattened results of applying the transformer to each
     * element of the source stage
     */
    <P> EventStage<P> flatTransform(Function<Try<T>, EventStage<P>> transformer,
                                    EventExecutor executor);

    /**
     * Adds a single observer to this {@code EventStage}.
     *
     * @param observer the observer to be added to this {@code EventStage}
     * @return the current {@code EventStage} with the added observer
     */
    default EventStage<T> observe(EventStageObserver<T> observer) {
        if (observer instanceof NoopEventPromise<T>) {
            return this;
        }
        return observe(Collections.singletonList(observer));
    }

    /**
     * Attaches an observer to the event stage that will be notified for each event or error.
     *
     * @param observer a BiConsumer where the first parameter is the event of type T and the second
     *                 parameter is a Throwable in case of an error.
     * @return a new EventStage with the observer attached, allowing for further chaining or
     * processing.
     */
    default EventStage<T> observe(BiConsumer<T, Throwable> observer) {
        return observe(EventStageObserver.create(observer));
    }

    /**
     * Registers a callback to be executed when the event completes, either successfully or with a
     * failure.
     *
     * @param onSuccess the consumer that will be called with the result if the event succeeds
     * @param onFailure the consumer that will be called with the exception if the event fails
     * @return an EventStage representing the stage of the event after the observer has been
     * attached
     */
    default EventStage<T> observe(Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        return observe(EventStageObserver.create(onSuccess, onFailure));
    }

    /**
     * Adds multiple observers to this {@code EventStage}.
     *
     * @param observers a collection of {@code EventStageListener} instances to be added to this
     *                  {@code EventStage}
     * @return the current {@code EventStage} with the added observers
     */
    EventStage<T> observe(Collection<EventStageObserver<T>> observers);

    /**
     * Converts the current event stage to a {@link CompletableFuture} that will be completed when
     * the event stage completes successfully or exceptionally.
     *
     * @return a new {@link CompletableFuture} that will complete with the result of the event
     * stage, or exceptionally if the event stage fails.
     */
    default CompletableFuture<T> toCompletableFuture() {
        CompletableFuture<T> future = new CompletableFuture<>();
        observe(new EventStageObserver<>() {
            @Override
            public void success(T value) {
                future.complete(value);
            }

            @Override
            public void failure(Throwable cause) {
                if (cause instanceof CancellationException) {
                    future.cancel(false);
                } else {
                    future.completeExceptionally(cause);
                }
            }
        });
        return future;
    }

    /**
     * Converts the current object into an {@code EventFuture} that can be used to handle the
     * asynchronous completion of an event.
     *
     * @return an {@code EventFuture<T>} representing the future result of the event
     */
    default EventFuture<T> toFuture() {
        if (isDone()) {
            return switch (getResult()) {
                case Success<T>(T v) -> new SucceedEventFuture<>(v, executor());
                case Failure<?>(Throwable e) -> new FailedEventFuture<>(e, executor());
                case null -> throw new RuntimeException("should never happen");
            };
        }
        return new DefaultEventFuture<>(this);
    }

    EventStage<T> withTimeout(long timeout, TimeUnit unit);
}
