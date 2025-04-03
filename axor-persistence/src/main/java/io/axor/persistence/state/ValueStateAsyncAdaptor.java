package io.axor.persistence.state;

import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventStage;

import java.util.concurrent.ExecutionException;

public class ValueStateAsyncAdaptor<V, C> implements ValueState<V, C> {
    private final EventExecutor executor;
    private final AsyncValueState<V, C> state;

    public ValueStateAsyncAdaptor(EventExecutor executor, AsyncValueState<V, C> state) {
        this.executor = executor;
        this.state = state;
    }

    private V awaitValue(EventStage<V> stage) {
        try {
            return stage.toFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void await(EventStage<Void> stage) {
        try {
            stage.toFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public V get() {
        return awaitValue(state.get(executor.newFuturePromise()));
    }

    @Override
    public void set(V value) {
        await(state.set(value, executor.newFuturePromise()));
    }

    @Override
    public V getAndSet(V value) {
        return awaitValue(state.getAndSet(value, executor.newFuturePromise()));
    }

    @Override
    public void remove() {
        await(state.remove(executor.newFuturePromise()));
    }

    @Override
    public V getAndRemove() {
        return awaitValue(state.getAndRemove(executor.newFuturePromise()));
    }

    @Override
    public void command(C command) {
        await(state.command(command, executor.newFuturePromise()));
    }

    @Override
    public V getAndCommand(C command) {
        return awaitValue(state.commandAndGet(command, executor.newFuturePromise()));
    }

    @Override
    public V commandAndGet(C command) {
        return awaitValue(state.commandAndGet(command, executor.newFuturePromise()));
    }
}
