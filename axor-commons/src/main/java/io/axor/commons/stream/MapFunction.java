package io.axor.commons.stream;

public interface MapFunction<T, P> {
    P map(T data) throws Throwable;
}
