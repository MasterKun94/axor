package io.axor.commons.stream;

public interface FilterFunction<T> {
    boolean filter(T data) throws Throwable;
}
