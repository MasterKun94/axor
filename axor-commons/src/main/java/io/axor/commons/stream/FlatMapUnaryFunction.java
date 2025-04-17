package io.axor.commons.stream;

public interface FlatMapUnaryFunction<T, P> extends FlatMapFunction<T, P> {
    EventUnary<P> map(T data) throws Throwable;
}
