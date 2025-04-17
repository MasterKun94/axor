package io.axor.commons.stream;

import io.axor.commons.concurrent.EventExecutor;

import java.util.Optional;
import java.util.function.Function;

import static io.axor.commons.stream.EventFlowOperators.filterOp;
import static io.axor.commons.stream.EventFlowOperators.flatmapStreamOrderedOp;
import static io.axor.commons.stream.EventFlowOperators.flatmapUnaryOrderedOp;
import static io.axor.commons.stream.EventFlowOperators.foldOp;
import static io.axor.commons.stream.EventFlowOperators.mapAsyncJucOrderedOp;
import static io.axor.commons.stream.EventFlowOperators.mapAsyncOrderedOp;
import static io.axor.commons.stream.EventFlowOperators.reduceOp;
import static io.axor.commons.stream.EventFlowOperators.reduceOptionOp;

public abstract class DefaultEventStream<T> extends DefaultEventFlow<T> implements EventStream<T> {

    public DefaultEventStream(EventExecutor executor) {
        super(executor);
    }

    @Override
    protected <P> DefaultEventFlow<P> newInstance(EventExecutor executor, Function<Subscriber<P>,
            Subscriber<T>> operator) {
        return newStreamInstance(executor, operator);
    }

    @Override
    public EventExecutor executor() {
        return super.executor();
    }

    @Override
    public EventStream<T> executor(EventExecutor executor) {
        return (EventStream<T>) super.executor(executor);
    }

    @Override
    public <P> EventStream<P> map(MapFunction<T, P> func) {
        return (EventStream<P>) super.map(func);
    }

    @Override
    public <P> EventStream<P> mapAsync(MapAsyncFunction<T, P> func) {
        return (EventStream<P>) super.mapAsync(func);
    }

    @Override
    public <P> EventStream<P> mapAsyncOrdered(MapAsyncFunction<T, P> func) {
        return newStreamInstance(executor(), sink -> mapAsyncOrderedOp(func, sink, executor()));
    }

    @Override
    public <P> EventStream<P> mapAsyncJuc(MapAsyncJucFunction<T, P> func) {
        return (EventStream<P>) super.mapAsyncJuc(func);
    }

    @Override
    public <P> EventStream<P> mapAsyncJucOrdered(MapAsyncJucFunction<T, P> func) {
        return newStreamInstance(executor(), sink -> mapAsyncJucOrderedOp(func, sink, executor()));
    }

    @Override
    public <P> EventStream<P> flatmapUnary(FlatMapUnaryFunction<T, P> func) {
        return (EventStream<P>) super.flatmapUnary(func);
    }

    @Override
    public <P> EventStream<P> flatmapStream(FlatMapStreamFunction<T, P> func) {
        return super.flatmapStream(func);
    }

    @Override
    public <P> EventStream<P> flatmapUnaryOrdered(FlatMapUnaryFunction<T, P> func) {
        return newStreamInstance(executor(), sink -> flatmapUnaryOrderedOp(func, sink, executor()));
    }

    @Override
    public <P> EventStream<P> flatmapStreamOrdered(FlatMapStreamFunction<T, P> func) {
        return newStreamInstance(executor(), sink -> flatmapStreamOrderedOp(func, sink,
                executor()));
    }

    @Override
    public EventStream<T> filter(FilterFunction<T> filter) {
        return newStreamInstance(executor(), sink -> filterOp(filter, sink));
    }

    @Override
    public EventUnary<T> reduce(ReduceFunction<T> reducer) {
        return newUnaryInstance(executor(), sink -> reduceOp(reducer, sink));
    }

    @Override
    public EventUnary<Optional<T>> reduceOption(ReduceFunction<T> reducer) {
        return newUnaryInstance(executor(), sink -> reduceOptionOp(reducer, sink));
    }

    @Override
    public <P> EventUnary<P> fold(P initial, FoldFunction<T, P> folder) {
        return newUnaryInstance(executor(), sink -> foldOp(initial, folder, sink));
    }

    @Override
    public EventStream<T> withSubscription(Subscriber<T> subscriber) {
        return (EventStream<T>) super.withSubscription(subscriber);
    }

    @Override
    public void subscribe(Subscriber<T> subscriber) {
        super.subscribe(subscriber);
    }
}
