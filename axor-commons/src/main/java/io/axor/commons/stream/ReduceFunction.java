package io.axor.commons.stream;

public interface ReduceFunction<T> {
    T reduce(T left, T right) throws Throwable;
}
