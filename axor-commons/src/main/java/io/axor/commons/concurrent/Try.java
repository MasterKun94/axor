package io.axor.commons.concurrent;

import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Represents the result of a computation that may either have succeeded with a value or failed with
 * an exception. This sealed interface can only be implemented by {@code Success} and
 * {@code Failure}.
 *
 * @param <T> the type of the value in case of success
 */
public sealed interface Try<T> permits Success, Failure {
    /**
     * Creates a new {@code Success} instance with the provided value, indicating that a computation
     * has succeeded.
     *
     * @param <T>   the type of the value in case of success
     * @param value the value representing the successful outcome of a computation
     * @return a new {@code Success} instance containing the given value
     */
    static <T> Success<T> success(T value) {
        return new Success<>(value);
    }

    /**
     * Creates a new {@code Failure} instance with the provided cause, indicating that a computation
     * has failed.
     *
     * @param <T>   the type of the value in case of success
     * @param cause the throwable representing the failure of a computation
     * @return a new {@code Failure} instance containing the given cause
     */
    static <T> Failure<T> failure(Throwable cause) {
        return new Failure<>(cause);
    }

    /**
     * Executes the given supplier and wraps the result in a {@code Try} instance.
     *
     * @param <T>      the type of the value in case of success
     * @param supplier the supplier to execute
     * @return a {@code Success} containing the result of the supplier if it completes successfully,
     * or a {@code Failure} containing the exception if the supplier throws an exception
     */
    static <T> Try<T> apply(Supplier<T> supplier) {
        try {
            return success(supplier.get());
        } catch (Throwable e) {
            return failure(e);
        }
    }

    /**
     * Applies the given function to the specified parameter and wraps the result in a {@code Try}
     * instance.
     *
     * @param <T>   the type of the input parameter
     * @param <P>   the type of the result after applying the function
     * @param func  the function to apply to the parameter
     * @param param the parameter to which the function is applied
     * @return a {@code Success} containing the result of the function if it completes successfully,
     * or a {@code Failure} containing the exception if the function throws an exception
     */
    static <T, P> Try<P> apply(Function<T, P> func, T param) {
        try {
            return success(func.apply(param));
        } catch (Throwable e) {
            return failure(e);
        }
    }

    /**
     * Determines whether the computation represented by this {@code Try} instance has succeeded.
     *
     * @return {@code true} if the computation has succeeded, otherwise {@code false}
     */
    boolean isSuccess();

    /**
     * Determines whether the computation represented by this {@code Try} instance has failed.
     *
     * @return {@code true} if the computation has failed, otherwise {@code false}
     */
    boolean isFailure();

    /**
     * Retrieves the value if the computation represented by this {@code Try} instance has
     * succeeded.
     *
     * @return the value of the successful computation, or {@code null} if the computation has
     * failed
     */
    @Nullable
    T value();

    /**
     * Retrieves the cause of the failure if the computation represented by this {@code Try}
     * instance has failed.
     *
     * @return the throwable representing the cause of the failure, or {@code null} if the
     * computation has succeeded
     */
    @Nullable
    Throwable cause();

    /**
     * Applies the given function to the value of this {@code Try} instance if it is a success, and
     * returns a new {@code Try} instance containing the result. If this {@code Try} instance
     * represents a failure, it is returned unchanged.
     *
     * @param <P>  the type of the result after applying the function
     * @param func the function to apply to the value of this {@code Try} instance if it is a
     *             success
     * @return a new {@code Try} instance containing the result of applying the function, or the
     * original failure if this {@code Try} instance is a failure
     */
    <P> Try<P> map(Function<T, P> func);

    /**
     * Applies the given function to the value of this {@code Try} instance if it is a success, and
     * returns a new {@code Try} instance containing the result. If this {@code Try} instance
     * represents a failure, it is returned unchanged.
     *
     * @param <P>  the type of the result after applying the function
     * @param func the function to apply to the value of this {@code Try} instance if it is a
     *             success. The function must return a {@code Try<P>} instance.
     * @return a new {@code Try} instance containing the result of applying the function, or the
     * original failure if this {@code Try} instance is a failure
     */
    <P> Try<P> flatmap(Function<T, Try<P>> func);

    /**
     * Transforms this {@code Try} instance by applying the given function to it and returns a new
     * {@code Try} instance containing the result.
     *
     * @param <P>  the type of the result after applying the function
     * @param func the function to apply to this {@code Try} instance
     * @return a new {@code Try} instance containing the result of applying the function
     */
    <P> Try<P> transform(Function<Try<T>, P> func);

    /**
     * Notifies the provided {@code EventPromise} with the result of the computation represented by
     * this {@code Try} instance. If the computation is a success, the value will be passed to the
     * promise. If the computation is a failure, the cause of the failure will be passed to the
     * promise.
     *
     * @param promise the {@code EventPromise} to notify with the result of the computation
     */
    void notify(EventPromise<T> promise);
}
