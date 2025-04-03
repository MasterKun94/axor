package io.axor.persistence.state;

import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventStage;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class MapStateAsyncAdaptor<K, V, C> implements MapState<K, V, C> {
    private final EventExecutor executor;
    private final AsyncMapState<K, V, C> state;

    public MapStateAsyncAdaptor(EventExecutor executor, AsyncMapState<K, V, C> state) {
        this.executor = executor;
        this.state = state;
    }

    private <P> P awaitValue(EventStage<P> stage) {
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
    public V get(K key) {
        return awaitValue(state.get(key, executor.newFuturePromise()));
    }

    @Override
    public void set(K key, V value) {
        await(state.set(key, value, executor.newFuturePromise()));
    }

    @Override
    public V getAndSet(K key, V value) {
        return awaitValue(state.getAndSet(key, value, executor.newFuturePromise()));
    }

    @Override
    public void remove(K key) {
        await(state.remove(key, executor.newFuturePromise()));
    }

    @Override
    public V getAndRemove(K key) {
        return awaitValue(state.getAndRemove(key, executor.newFuturePromise()));
    }

    @Override
    public void command(K key, C command) {
        await(state.command(key, command, executor.newFuturePromise()));
    }

    @Override
    public V getAndCommand(K key, C command) {
        return awaitValue(state.getAndCommand(key, command, executor.newFuturePromise()));
    }

    @Override
    public V commandAndGet(K key, C command) {
        return awaitValue(state.commandAndGet(key, command, executor.newFuturePromise()));
    }

    @Override
    public List<V> getBatch(List<K> keys) {
        return awaitValue(state.getBatch(keys, executor.newFuturePromise()));
    }

    @Override
    public void setBatch(List<K> keys, List<V> values) {
        await(state.setBatch(keys, values, executor.newFuturePromise()));
    }

    @Override
    public List<V> getAndSetBatch(List<K> keys, List<V> values) {
        return awaitValue(state.getAndSetBatch(keys, values, executor.newFuturePromise()));
    }

    @Override
    public void removeBatch(List<K> keys) {
        await(state.removeBatch(keys, executor.newFuturePromise()));
    }

    @Override
    public List<V> getAndRemoveBatch(List<K> keys) {
        return awaitValue(state.getAndRemoveBatch(keys, executor.newFuturePromise()));
    }

    @Override
    public void commandBatch(List<K> keys, C command) {
        await(state.commandBatch(keys, command, executor.newFuturePromise()));
    }

    @Override
    public List<V> getAndCommandBatch(List<K> keys, C command) {
        return awaitValue(state.getAndCommandBatch(keys, command, executor.newFuturePromise()));
    }

    @Override
    public List<V> commandAndGetBatch(List<K> keys, C command) {
        return awaitValue(state.commandAndGetBatch(keys, command, executor.newFuturePromise()));
    }
}
