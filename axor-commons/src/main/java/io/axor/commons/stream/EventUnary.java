package io.axor.commons.stream;

import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventStage;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface EventUnary<T> extends EventFlow<T> {
    @Override
    EventExecutor executor();

    @Override
    EventUnary<T> executor(EventExecutor executor);

    @Override
    <P> EventUnary<P> map(MapFunction<T, P> func);

    @Override
    <P> EventUnary<P> mapAsync(MapAsyncFunction<T, P> func);

    @Override
    <P> EventUnary<P> mapAsyncJuc(MapAsyncJucFunction<T, P> func);

    @Override
    <P> EventUnary<P> flatmapUnary(FlatMapUnaryFunction<T, P> func);

    @Override
    <P> EventStream<P> flatmapStream(FlatMapStreamFunction<T, P> func);

    @Override
    EventUnary<T> withSubscription(Subscriber<T> subscriber);

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

    default EventStage<T> subscribe() {
        var promise = executor().<T>newPromise();
        subscribe(promise::success, signal -> {
            if (signal instanceof ErrorSignal(var cause)) {
                promise.failure(cause);
            }
        }, () -> {
        });
        return promise;
    }
}
