package io.axor.commons.stream;

public interface FlatMapStreamFunction<T, P> extends FlatMapFunction<T, P> {
    EventStream<P> map(T data) throws Throwable;
}
