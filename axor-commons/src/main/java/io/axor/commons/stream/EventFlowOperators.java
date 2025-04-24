package io.axor.commons.stream;

import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;
import io.axor.commons.concurrent.Failure;
import io.axor.commons.concurrent.Success;
import io.axor.commons.stream.EventFlow.Signal;
import io.axor.commons.stream.EventFlow.Subscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class EventFlowOperators {
    public static <T> Subscriber<T> asyncOp(EventExecutor executor,
                                            Subscriber<T> sink) {
        var internal = new SubscriberInternal<>(sink);
        return new Subscriber<>() {
            private volatile boolean continueFlag = true;

            @Override
            public void onEvent(T event) {
                executor.execute(() -> {
                    try {
                        internal.onEvent(event);
                    } catch (Throwable e) {
                        internal.onSignal(new ErrorSignal(e));
                    } finally {
                        //noinspection NonAtomicOperationOnVolatileField
                        continueFlag = continueFlag && internal.continueFlag();
                    }
                });
            }

            @Override
            public void onSignal(Signal signal) {
                executor.execute(() -> {
                    try {
                        internal.onSignal(signal);
                    } finally {
                        //noinspection NonAtomicOperationOnVolatileField
                        continueFlag = continueFlag && internal.continueFlag();
                    }
                });
            }

            @Override
            public void onEnd() {
                executor.execute(internal::onEnd);
            }

            @Override
            public boolean continueFlag() {
                return continueFlag;
            }
        };
    }

    public static <T, P> Subscriber<T> mapOp(MapFunction<T, P> func,
                                             Subscriber<P> sink) {
        return new Subscriber<>() {

            @Override
            public void onEvent(T event) {
                try {
                    sink.onEvent(func.map(event));
                } catch (Throwable e) {
                    sink.onSignal(new ErrorSignal(e));
                }
            }

            @Override
            public void onSignal(Signal signal) {
                sink.onSignal(signal);
            }

            @Override
            public void onEnd() {
                sink.onEnd();
            }

            @Override
            public boolean continueFlag() {
                return sink.continueFlag();
            }
        };
    }

    public static <T, P> Subscriber<T> mapAsyncOrderedOp(MapAsyncFunction<T, P> func,
                                                         Subscriber<P> sink,
                                                         EventExecutor executor) {
        return new Subscriber<>() {
            private EventStage<P> prevStage;

            @Override
            public void onEvent(T event) {
                EventStage<P> prevStage = this.prevStage;
                try {
                    if (prevStage == null || prevStage.isDone()) {
                        this.prevStage = func.map(event).transform(t -> {
                            switch (t) {
                                case Success(var value) -> sink.onEvent(value);
                                case Failure(var cause) -> sink.onSignal(new ErrorSignal(cause));
                                default -> throw new IllegalArgumentException();
                            }
                            return t;
                        }, executor);
                    } else {
                        this.prevStage = func.map(event).flatTransform(t -> {
                            switch (t) {
                                case Success(var value) -> {
                                    if (prevStage.isDone()) {
                                        sink.onEvent(value);
                                        return EventStage.succeed(value, executor);
                                    } else {
                                        return prevStage.transform(t0 -> {
                                            sink.onEvent(value);
                                            return t0;
                                        });
                                    }
                                }
                                case Failure(var cause) -> {
                                    if (prevStage.isDone()) {
                                        sink.onSignal(new ErrorSignal(cause));
                                        return EventStage.failed(cause, executor);
                                    } else {
                                        return prevStage.transform(t0 -> {
                                            sink.onSignal(new ErrorSignal(cause));
                                            return t0;
                                        });
                                    }
                                }
                                default -> throw new IllegalArgumentException();
                            }
                        }, executor);
                    }
                } catch (Throwable e) {
                    sink.onSignal(new ErrorSignal(e));
                }
            }

            @Override
            public void onSignal(Signal signal) {
                if (prevStage == null || prevStage.isDone()) {
                    sink.onSignal(signal);
                } else {
                    prevStage.observe((p, e) -> sink.onSignal(signal));
                }
            }

            @Override
            public void onEnd() {
                if (prevStage == null || prevStage.isDone()) {
                    sink.onEnd();
                } else {
                    prevStage.observe((p, e) -> sink.onEnd());
                }
            }

            @Override
            public boolean continueFlag() {
                return sink.continueFlag();
            }
        };
    }

    public static <T, P> Subscriber<T> mapAsyncOp(MapAsyncFunction<T, P> func,
                                                  Subscriber<P> sink,
                                                  EventExecutor executor) {
        return new Subscriber<>() {
            private final AtomicInteger numPending = new AtomicInteger();

            @Override
            public void onEvent(T event) {
                numPending.incrementAndGet();
                try {
                    func.map(event).executor(executor).observe(value -> {
                        try {
                            sink.onEvent(value);
                        } finally {
                            if (numPending.decrementAndGet() == -1) {
                                sink.onEnd();
                            }
                        }
                    }, cause -> {
                        try {
                            sink.onSignal(new ErrorSignal(cause));
                        } finally {
                            if (numPending.decrementAndGet() == -1) {
                                sink.onEnd();
                            }
                        }
                    });
                } catch (Throwable e) {
                    try {
                        sink.onSignal(new ErrorSignal(e));
                    } finally {
                        if (numPending.decrementAndGet() == -1) {
                            sink.onEnd();
                        }
                    }
                }
            }

            @Override
            public void onSignal(Signal signal) {
                sink.onSignal(signal);
            }

            @Override
            public void onEnd() {
                if (numPending.decrementAndGet() == -1) {
                    sink.onEnd();
                }
            }

            @Override
            public boolean continueFlag() {
                return sink.continueFlag();
            }
        };
    }

    public static <T, P> Subscriber<T> mapAsyncJucOrderedOp(MapAsyncJucFunction<T, P> func,
                                                            Subscriber<P> sink,
                                                            EventExecutor executor) {
        return new Subscriber<>() {
            private CompletableFuture<P> prevStage;

            @Override
            public void onEvent(T event) {
                CompletableFuture<P> prevStage = this.prevStage;
                try {
                    if (prevStage == null || prevStage.isDone()) {
                        this.prevStage = func.map(event).whenCompleteAsync((v, e) -> {
                            if (e != null) {
                                sink.onSignal(new ErrorSignal(e));
                            } else {
                                sink.onEvent(v);
                            }
                        }, executor);
                    } else {
                        CompletableFuture<P> currentStage = new CompletableFuture<>();
                        func.map(event).whenCompleteAsync((v, e) -> {
                            try {
                                if (e != null) {
                                    if (prevStage.isDone()) {
                                        sink.onSignal(new ErrorSignal(e));
                                        currentStage.completeExceptionally(e);
                                    } else {
                                        prevStage.whenCompleteAsync((v0, e0) -> {
                                            sink.onSignal(new ErrorSignal(e));
                                            currentStage.completeExceptionally(e);
                                        }, executor);
                                    }
                                } else {
                                    if (prevStage.isDone()) {
                                        sink.onEvent(v);
                                        currentStage.complete(v);
                                    } else {
                                        prevStage.whenCompleteAsync((v0, e0) -> {
                                            sink.onEvent(v);
                                            currentStage.complete(v);
                                        }, executor);
                                    }
                                }
                            } catch (Throwable ex) {
                                currentStage.completeExceptionally(ex);
                            }
                        }, executor);
                        this.prevStage = currentStage;
                    }
                } catch (Throwable e) {
                    sink.onSignal(new ErrorSignal(e));
                }
            }

            @Override
            public void onSignal(Signal signal) {
                if (prevStage == null || prevStage.isDone()) {
                    sink.onSignal(signal);
                } else {
                    prevStage.whenCompleteAsync((v, e) -> sink.onSignal(signal), executor);
                }
            }

            @Override
            public void onEnd() {
                if (prevStage == null || prevStage.isDone()) {
                    sink.onEnd();
                } else {
                    prevStage.whenCompleteAsync((v, e) -> sink.onEnd(), executor);
                }
            }

            @Override
            public boolean continueFlag() {
                return sink.continueFlag();
            }
        };
    }

    public static <T, P> Subscriber<T> mapAsyncJucOp(MapAsyncJucFunction<T, P> func,
                                                     Subscriber<P> sink,
                                                     EventExecutor executor) {
        return new Subscriber<>() {
            private final AtomicInteger numPending = new AtomicInteger();

            @Override
            public void onEvent(T event) {
                numPending.incrementAndGet();
                try {
                    func.map(event).whenCompleteAsync((v, e) -> {
                        try {
                            if (e != null) {
                                sink.onSignal(new ErrorSignal(e));
                            } else {
                                sink.onEvent(v);
                            }
                        } finally {
                            if (numPending.decrementAndGet() == -1) {
                                sink.onEnd();
                            }
                        }
                    }, executor);
                } catch (Throwable e) {
                    try {
                        sink.onSignal(new ErrorSignal(e));
                    } finally {
                        if (numPending.decrementAndGet() == -1) {
                            sink.onEnd();
                        }
                    }
                }
            }

            @Override
            public void onSignal(Signal signal) {
                sink.onSignal(signal);
            }

            @Override
            public void onEnd() {
                if (numPending.decrementAndGet() == -1) {
                    sink.onEnd();
                }
            }

            @Override
            public boolean continueFlag() {
                return sink.continueFlag();
            }
        };
    }

    public static <T, P> Subscriber<T> flatmapUnaryOrderedOp(FlatMapUnaryFunction<T, P> func,
                                                             Subscriber<P> sink,
                                                             EventExecutor executor) {
        return mapAsyncOrderedOp(t -> func.map(t).subscribe(), sink, executor);
    }

    public static <T, P> Subscriber<T> flatmapStreamOrderedOp(FlatMapStreamFunction<T, P> func,
                                                              Subscriber<P> sink,
                                                              EventExecutor executor) {
        return new Subscriber<>() {
            private EventStage<Void> prevStage;

            @Override
            public void onEvent(T event) {
                EventStage<Void> prevStage = this.prevStage;
                EventFlow<P> stream;
                try {
                    stream = func.map(event).executor(executor);
                } catch (Throwable e) {
                    sink.onSignal(new ErrorSignal(e));
                    return;
                }
                EventPromise<Void> promise = executor.newPromise();
                this.prevStage = promise;
                if (prevStage == null || prevStage.isDone()) {
                    stream.subscribe(sink::onEvent, sink::onSignal,
                            () -> promise.success(null));
                } else {
                    List<Consumer<Subscriber<P>>> buffer = new ArrayList<>();
                    EventStage<Void> safePrevStage = prevStage.executor(executor)
                            .transform(t -> {
                                if (!buffer.isEmpty()) {
                                    for (var consumer : buffer) {
                                        consumer.accept(sink);
                                    }
                                    buffer.clear();
                                }
                                return t;
                            });
                    stream.subscribe(e -> {
                        if (safePrevStage.isDone()) {
                            assert buffer.isEmpty();
                            sink.onEvent(e);
                        } else buffer.add(s -> s.onEvent(e));
                    }, e -> {
                        if (safePrevStage.isDone()) {
                            assert buffer.isEmpty();
                            sink.onSignal(e);
                        } else buffer.add(s -> s.onSignal(e));
                    }, () -> safePrevStage.observe(promise));
                }
            }

            @Override
            public void onSignal(Signal signal) {
                if (prevStage == null || prevStage.isDone()) {
                    sink.onSignal(signal);
                } else {
                    prevStage.observe((v, t) -> sink.onSignal(signal));
                }
            }

            @Override
            public void onEnd() {
                if (prevStage == null || prevStage.isDone()) {
                    sink.onEnd();
                } else {
                    prevStage.observe((v, t) -> sink.onEnd());
                }
            }

            @Override
            public boolean continueFlag() {
                return sink.continueFlag();
            }
        };
    }

    public static <T, P> Subscriber<T> flatmapOp(FlatMapFunction<T, P> func,
                                                 Subscriber<P> sink,
                                                 EventExecutor executor) {
        return new Subscriber<>() {
            private final AtomicInteger numPending = new AtomicInteger();

            @Override
            public void onEvent(T event) {
                numPending.incrementAndGet();
                try {
                    func.map(event).executor(executor).subscribe(new Subscriber<>() {
                        @Override
                        public void onEvent(P event) {
                            sink.onEvent(event);
                        }

                        @Override
                        public void onSignal(Signal signal) {
                            sink.onSignal(signal);
                        }

                        @Override
                        public void onEnd() {
                            if (numPending.decrementAndGet() == -1) {
                                sink.onEnd();
                            }
                        }

                        @Override
                        public boolean continueFlag() {
                            return sink.continueFlag();
                        }
                    });
                } catch (Throwable e) {
                    try {
                        sink.onSignal(new ErrorSignal(e));
                    } finally {
                        if (numPending.decrementAndGet() == -1) {
                            sink.onEnd();
                        }
                    }

                }
            }

            @Override
            public void onSignal(Signal signal) {
                sink.onSignal(signal);
            }

            @Override
            public void onEnd() {
                if (numPending.decrementAndGet() == -1) {
                    sink.onEnd();
                }
            }

            @Override
            public boolean continueFlag() {
                return sink.continueFlag();
            }
        };
    }

    public static <T> Subscriber<T> filterOp(FilterFunction<T> filter,
                                             Subscriber<T> sink) {
        return new Subscriber<>() {
            @Override
            public void onEvent(T event) {
                try {
                    if (filter.filter(event)) {
                        sink.onEvent(event);
                    }
                } catch (Throwable e) {
                    sink.onSignal(new ErrorSignal(e));
                }
            }

            @Override
            public void onSignal(Signal signal) {
                sink.onSignal(signal);
            }

            @Override
            public void onEnd() {
                sink.onEnd();
            }

            @Override
            public boolean continueFlag() {
                return sink.continueFlag();
            }
        };
    }

    public static <T> Subscriber<T> reduceOp(ReduceFunction<T> reducer,
                                             Subscriber<T> sink) {
        return new Subscriber<>() {
            T left;
            private boolean continueFlag = true;

            @Override
            public void onEvent(T event) {
                if (left == null) {
                    left = event;
                } else {
                    try {
                        left = reducer.reduce(left, event);
                    } catch (Throwable e) {
                        onSignal(new ErrorSignal(e));
                    }
                }
            }

            @Override
            public void onSignal(Signal signal) {
                try {
                    sink.onSignal(signal);
                } finally {
                    continueFlag = false;
                    sink.onEnd();
                }
            }

            @Override
            public void onEnd() {
                try {
                    if (left == null) {
                        sink.onSignal(new ErrorSignal(new NoSuchElementException()));
                    } else {
                        sink.onEvent(left);
                    }
                } finally {
                    continueFlag = false;
                    sink.onEnd();
                }
            }

            @Override
            public boolean continueFlag() {
                return continueFlag;
            }
        };
    }

    public static <T> Subscriber<T> reduceOptionOp(ReduceFunction<T> reducer,
                                                   Subscriber<Optional<T>> sink) {
        return new Subscriber<>() {
            T left;
            private boolean continueFlag = true;

            @Override
            public void onEvent(T event) {
                if (left == null) {
                    left = event;
                } else {
                    try {
                        left = reducer.reduce(left, event);
                    } catch (Throwable e) {
                        onSignal(new ErrorSignal(e));
                    }
                }
            }

            @Override
            public void onSignal(Signal signal) {
                try {
                    sink.onSignal(signal);
                } finally {
                    continueFlag = false;
                    sink.onEnd();
                }
            }

            @Override
            public void onEnd() {
                try {
                    sink.onEvent(Optional.ofNullable(left));
                } finally {
                    continueFlag = false;
                    sink.onEnd();
                }
            }

            @Override
            public boolean continueFlag() {
                return continueFlag;
            }
        };
    }

    public static <T, P> Subscriber<T> foldOp(P initial,
                                              FoldFunction<T, P> folder,
                                              Subscriber<P> sink) {
        return new Subscriber<>() {
            P left = initial;
            private boolean continueFlag = true;

            @Override
            public void onEvent(T event) {
                try {
                    left = folder.fold(left, event);
                } catch (Throwable e) {
                    onSignal(new ErrorSignal(e));
                }
            }

            @Override
            public void onSignal(Signal signal) {
                try {
                    sink.onSignal(signal);
                } finally {
                    continueFlag = false;
                    sink.onEnd();
                }
            }

            @Override
            public void onEnd() {
                try {
                    if (left == null) {
                        sink.onSignal(new ErrorSignal(new NoSuchElementException()));
                    } else {
                        sink.onEvent(left);
                    }
                } finally {
                    continueFlag = false;
                    sink.onEnd();
                }
            }

            @Override
            public boolean continueFlag() {
                return continueFlag;
            }
        };
    }
}
