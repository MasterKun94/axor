package io.axor.commons.stream;

import io.axor.commons.concurrent.EventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.axor.commons.stream.EventFlowOperators.asyncOp;
import static io.axor.commons.stream.EventFlowOperators.flatmapOp;
import static io.axor.commons.stream.EventFlowOperators.mapAsyncJucOp;
import static io.axor.commons.stream.EventFlowOperators.mapAsyncOp;
import static io.axor.commons.stream.EventFlowOperators.mapOp;

public abstract class DefaultEventFlow<T> implements EventFlow<T> {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultEventFlow.class);

    private final EventExecutor executor;
    private List<Subscriber<T>> sideObservers;

    public DefaultEventFlow(EventExecutor executor) {
        this.executor = executor;
    }

    protected abstract <P> DefaultEventFlow<P> newInstance(EventExecutor executor,
                                                           Function<Subscriber<P>, Subscriber<T>> operator);

    protected <P> DefaultEventStream<P> newStreamInstance(EventExecutor executor,
                                                          Function<Subscriber<P>, Subscriber<T>> operator) {
        return new DefaultEventStream<>(executor) {
            @Override
            public void doExecute(Subscriber<P> subscriber) {
                DefaultEventFlow.this.subscribe(operator.apply(subscriber));
            }
        };
    }

    protected <P> DefaultEventUnary<P> newUnaryInstance(EventExecutor executor,
                                                        Function<Subscriber<P>, Subscriber<T>> operator) {
        return new DefaultEventUnary<>(executor) {
            @Override
            public void doExecute(Subscriber<P> subscriber) {
                DefaultEventFlow.this.subscribe(operator.apply(subscriber));
            }
        };
    }

    protected abstract void doExecute(Subscriber<T> subscriber);

    @Override
    public EventExecutor executor() {
        return executor;
    }

    @Override
    public void subscribe(Subscriber<T> subscriber) {
        SubscriberInternal<T> internal = new SubscriberInternal<>(subscriber);
        if (sideObservers == null) {
            doExecute(internal);
        } else {
            doExecute(new Subscriber<>() {
                @Override
                public void onEvent(T event) {
                    for (Subscriber<T> sideSink : sideObservers) {
                        try {
                            if (sideSink.continueFlag()) {
                                sideSink.onEvent(event);
                            }
                        } catch (Throwable e) {
                            LOG.error("Unexpected side sink error on event", e);
                        }
                    }
                    if (internal.continueFlag()) {
                        internal.onEvent(event);
                    }
                }

                @Override
                public void onEnd() {
                    for (Subscriber<T> sideSink : sideObservers) {
                        try {
                            sideSink.onEnd();
                        } catch (Throwable e) {
                            LOG.error("Unexpected side sink error on complete", e);
                        }
                    }
                    internal.onEnd();
                }

                @Override
                public void onErrorEvent(Throwable error) {
                    for (Subscriber<T> sideSink : sideObservers) {
                        try {
                            if (sideSink.continueFlag()) {
                                sideSink.onErrorEvent(error);
                            }
                        } catch (Throwable e) {
                            LOG.error("Unexpected side sink error on error", e);
                        }
                    }
                    if (internal.continueFlag()) {
                        internal.onErrorEvent(error);
                    }
                }

                @Override
                public boolean continueFlag() {
                    return internal.continueFlag();
                }
            });
        }
    }

    @Override
    public EventFlow<T> executor(EventExecutor executor) {
        if (this.executor.equals(executor)) {
            return this;
        } else {
            return newInstance(executor, sink -> asyncOp(executor, sink));
        }
    }

    @Override
    public <P> EventFlow<P> map(MapFunction<T, P> func) {
        return newInstance(executor, sink -> mapOp(func, sink));
    }

    @Override
    public <P> EventFlow<P> mapAsync(MapAsyncFunction<T, P> func) {
        return newInstance(executor, sink -> mapAsyncOp(func, sink, executor));
    }

    @Override
    public <P> EventFlow<P> mapAsyncJuc(MapAsyncJucFunction<T, P> func) {
        return newInstance(executor, sink -> mapAsyncJucOp(func, sink, executor));
    }

    @Override
    public <P> EventFlow<P> flatmapUnary(FlatMapUnaryFunction<T, P> func) {
        return newInstance(executor, sink -> flatmapOp(func, sink, executor));
    }

    @Override
    public <P> EventStream<P> flatmapStream(FlatMapStreamFunction<T, P> func) {
        return newStreamInstance(executor, sink -> flatmapOp(func, sink, executor));
    }

    @Override
    public EventFlow<T> withSubscription(Subscriber<T> subscriber) {
        if (sideObservers == null) {
            sideObservers = new ArrayList<>();
        }
        sideObservers.add(subscriber);
        return this;
    }
}
