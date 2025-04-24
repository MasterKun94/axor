package io.axor.commons.stream;

import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventStage;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface EventFlow<T> {

    static <T> EventStream<T> createStream(Publisher<T> publisher, EventExecutor executor) {
        return new DefaultEventStream<>(executor) {
            @Override
            protected void doExecute(Subscriber<T> subscriber) {
                executor().execute(() -> {
                    try {
                        publisher.publishTo(subscriber);
                    } catch (Throwable e) {
                        try {
                            subscriber.onSignal(new ErrorSignal(e));
                        } finally {
                            subscriber.onEnd();
                        }
                    }
                });
            }
        };
    }

    static <T> EventUnary<T> createUnary(EventStage<T> stage) {
        return new DefaultEventUnary<T>(stage.executor()) {
            @Override
            protected void doExecute(Subscriber<T> subscriber) {
                stage.observe(v -> {
                    try {
                        subscriber.onEvent(v);
                    } finally {
                        subscriber.onEnd();
                    }
                }, e -> {
                    try {
                        subscriber.onSignal(new ErrorSignal(e));
                    } finally {
                        subscriber.onEnd();
                    }
                });
            }
        };
    }

    static <T> EventUnary<T> createUnary(Supplier<T> supplier, EventExecutor executor) {
        return new DefaultEventUnary<>(executor) {
            @Override
            protected void doExecute(Subscriber<T> subscriber) {
                executor().execute(() -> {
                    try {
                        subscriber.onEvent(supplier.get());
                    } catch (Throwable e) {
                        subscriber.onSignal(new ErrorSignal(e));
                    } finally {
                        subscriber.onEnd();
                    }
                });
            }
        };
    }

    EventExecutor executor();

    EventFlow<T> executor(EventExecutor executor);

    <P> EventFlow<P> map(MapFunction<T, P> func);

    <P> EventFlow<P> mapAsync(MapAsyncFunction<T, P> func);

    <P> EventFlow<P> mapAsyncJuc(MapAsyncJucFunction<T, P> func);

    <P> EventFlow<P> flatmapUnary(FlatMapUnaryFunction<T, P> func);

    <P> EventStream<P> flatmapStream(FlatMapStreamFunction<T, P> func);

    EventFlow<T> withSubscription(Subscriber<T> subscriber);

    void subscribe(Subscriber<T> subscriber);

    default void subscribe(Consumer<T> onEvent, Consumer<Signal> onSignal, Runnable onComplete) {
        subscribe(onEvent, onSignal, onComplete, () -> true);
    }

    default void subscribe(Consumer<T> onEvent, Consumer<Signal> onSignal, Runnable onComplete,
                           BooleanSupplier continueFlag) {
        subscribe(new Subscriber<>() {
            @Override
            public void onEvent(T event) {
                onEvent.accept(event);
            }

            @Override
            public void onSignal(Signal signal) {
                onSignal.accept(signal);
            }

            @Override
            public void onEnd() {
                onComplete.run();
            }

            @Override
            public boolean continueFlag() {
                return continueFlag.getAsBoolean();
            }
        });
    }

    interface Subscriber<T> {
        void onEvent(T event);

        void onSignal(Signal signal);

        void onEnd();

        default boolean continueFlag() {
            return true;
        }
    }

    interface Publisher<T> {
        void publishTo(Subscriber<T> subscriber);
    }

    interface Signal {
    }
}
