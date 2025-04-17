package io.axor.commons.stream;

public interface FlatMapFunction<T, P> {
    EventFlow<P> map(T data) throws Throwable;
}
