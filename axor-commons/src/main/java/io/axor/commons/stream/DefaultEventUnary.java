package io.axor.commons.stream;

import io.axor.commons.concurrent.EventExecutor;

import java.util.function.Function;

public abstract class DefaultEventUnary<T> extends DefaultEventFlow<T> implements EventUnary<T> {

    public DefaultEventUnary(EventExecutor executor) {
        super(executor);
    }

    @Override
    protected abstract void doExecute(Subscriber<T> subscriber);

    @Override
    protected <P> DefaultEventFlow<P> newInstance(EventExecutor executor,
                                                  Function<Subscriber<P>, Subscriber<T>> operator) {
        return newUnaryInstance(executor, operator);
    }

    @Override
    public EventExecutor executor() {
        return super.executor();
    }

    @Override
    public EventUnary<T> executor(EventExecutor executor) {
        return (EventUnary<T>) super.executor(executor);
    }

    @Override
    public <P> EventUnary<P> map(MapFunction<T, P> func) {
        return (EventUnary<P>) super.map(func);
    }

    @Override
    public <P> EventUnary<P> mapAsync(MapAsyncFunction<T, P> func) {
        return (EventUnary<P>) super.mapAsync(func);
    }

    @Override
    public <P> EventUnary<P> mapAsyncJuc(MapAsyncJucFunction<T, P> func) {
        return (EventUnary<P>) super.mapAsyncJuc(func);
    }

    @Override
    public <P> EventUnary<P> flatmapUnary(FlatMapUnaryFunction<T, P> func) {
        return (EventUnary<P>) super.flatmapUnary(func);
    }

    @Override
    public <P> EventStream<P> flatmapStream(FlatMapStreamFunction<T, P> func) {
        return super.flatmapStream(func);
    }

    @Override
    public EventUnary<T> withSubscription(Subscriber<T> subscriber) {
        return (EventUnary<T>) super.withSubscription(subscriber);
    }

    @Override
    public void subscribe(Subscriber<T> subscriber) {
        super.subscribe(subscriber);
    }
}
