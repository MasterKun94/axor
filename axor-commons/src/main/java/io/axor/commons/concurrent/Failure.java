package io.axor.commons.concurrent;

import java.util.function.Function;

/**
 * Represents the result of a computation that has failed, encapsulating the cause of the failure.
 * This record is a concrete implementation of the {@code Try} interface and indicates that a
 * computation did not succeed.
 *
 * @param <T> the type of the value in case of success (not applicable for this instance)
 */
public record Failure<T>(Throwable cause) implements Try<T> {
    @Override
    public boolean isSuccess() {
        return false;
    }

    @Override
    public boolean isFailure() {
        return true;
    }

    @Override
    public T value() {
        throw new RuntimeException("Computation failed: " + cause.getMessage(), cause);
    }

    @SuppressWarnings("unchecked")
    public <P> Try<P> cast() {
        return (Try<P>) this;
    }

    @Override
    public <P> Try<P> map(Function<T, P> func) {
        return new Failure<>(cause);
    }

    @Override
    public <P> Try<P> flatmap(Function<T, Try<P>> func) {
        return new Failure<>(cause);
    }

    @Override
    public <P> Try<P> transform(Function<Try<T>, P> func) {
        return Try.failure(new RuntimeException("Transformation failed: " + cause.getMessage(),
                cause));
    }

    @Override
    public void notify(EventPromise<T> promise) {
        promise.failure(cause);
    }
}
