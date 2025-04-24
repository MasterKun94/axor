package io.axor.commons.stream;

import io.axor.commons.concurrent.EventExecutor;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface EventStream<T> extends EventFlow<T> {
    @Override
    EventExecutor executor();

    @Override
    EventStream<T> executor(EventExecutor executor);

    @Override
    <P> EventStream<P> map(MapFunction<T, P> func);

    @Override
    <P> EventStream<P> mapAsync(MapAsyncFunction<T, P> func);

    <P> EventStream<P> mapAsyncOrdered(MapAsyncFunction<T, P> func);

    @Override
    <P> EventStream<P> mapAsyncJuc(MapAsyncJucFunction<T, P> func);

    <P> EventStream<P> mapAsyncJucOrdered(MapAsyncJucFunction<T, P> func);

    @Override
    <P> EventStream<P> flatmapUnary(FlatMapUnaryFunction<T, P> func);

    @Override
    <P> EventStream<P> flatmapStream(FlatMapStreamFunction<T, P> func);

    <P> EventStream<P> flatmapUnaryOrdered(FlatMapUnaryFunction<T, P> func);

    <P> EventStream<P> flatmapStreamOrdered(FlatMapStreamFunction<T, P> func);

    EventStream<T> filter(FilterFunction<T> filter);

    EventUnary<T> reduce(ReduceFunction<T> reducer);

    EventUnary<Optional<T>> reduceOption(ReduceFunction<T> reducer);

    <P> EventUnary<P> fold(P initial, FoldFunction<T, P> folder);

    default EventStream<T> filterNot(FilterFunction<T> filter) {
        return filter(d -> !filter.filter(d));
    }

    @Override
    EventStream<T> withSubscription(Subscriber<T> subscriber);

    @Override
    void subscribe(Subscriber<T> subscriber);

    @Override
    default void subscribe(Consumer<T> onEvent, Consumer<Signal> onSignal, Runnable onComplete) {
        EventFlow.super.subscribe(onEvent, onSignal, onComplete);
    }

    @Override
    default void subscribe(Consumer<T> onEvent, Consumer<Signal> onSignal, Runnable onComplete,
                           BooleanSupplier continueFlag) {
        EventFlow.super.subscribe(onEvent, onSignal, onComplete, continueFlag);
    }
}
