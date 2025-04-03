package io.axor.commons.concurrent;

import java.util.function.Function;

/**
 * Represents a successful computation with a value. This record is an implementation of the
 * {@code Try<T>} interface, indicating that a computation has succeeded.
 *
 * @param <T> the type of the value in case of success
 */
public record Success<T>(T value) implements Try<T> {
    @Override
    public boolean isSuccess() {
        return true;
    }

    @Override
    public boolean isFailure() {
        return false;
    }

    @Override
    public Throwable cause() {
        return null;
    }

    @Override
    public <P> Try<P> map(Function<T, P> func) {
        return Try.apply(func, value);
    }

    @Override
    public <P> Try<P> flatmap(Function<T, Try<P>> func) {
        try {
            return func.apply(value);
        } catch (Throwable e) {
            return Try.failure(e);
        }
    }

    @Override
    public <P> Try<P> transform(Function<Try<T>, P> func) {
        return Try.apply(func, this);
    }

    @Override
    public void notify(EventPromise<T> promise) {
        promise.success(value);
    }
}
