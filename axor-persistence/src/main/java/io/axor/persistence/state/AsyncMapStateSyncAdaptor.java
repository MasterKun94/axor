package io.axor.persistence.state;

import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

public class AsyncMapStateSyncAdaptor<K, V, C> implements AsyncMapState<K, V, C> {
    private final Executor taskExecutor;
    private final MapState<K, V, C> state;

    public AsyncMapStateSyncAdaptor(Executor taskExecutor, MapState<K, V, C> state) {
        this.taskExecutor = taskExecutor;
        this.state = state;
    }

    private <P> EventStage<P> supplyAsync(Callable<P> callable, EventPromise<P> promise) {
        taskExecutor.execute(() -> {
            try {
                promise.success(callable.call());
            } catch (Throwable t) {
                promise.failure(t);
            }
        });
        return promise;
    }

    private EventStage<Void> runAsync(Runnable runnable, EventPromise<Void> promise) {
        taskExecutor.execute(() -> {
            try {
                runnable.run();
                promise.success(null);
            } catch (Throwable t) {
                promise.failure(t);
            }
        });
        return promise;
    }

    @Override
    public EventStage<V> get(K key, EventPromise<V> promise) {
        return supplyAsync(() -> state.get(key), promise);
    }

    @Override
    public EventStage<Void> set(K key, V value, EventPromise<Void> promise) {
        return runAsync(() -> state.set(key, value), promise);
    }

    @Override
    public EventStage<V> getAndSet(K key, V value, EventPromise<V> promise) {
        return supplyAsync(() -> state.getAndSet(key, value), promise);
    }

    @Override
    public EventStage<Void> remove(K key, EventPromise<Void> promise) {
        return runAsync(() -> state.remove(key), promise);
    }

    @Override
    public EventStage<V> getAndRemove(K key, EventPromise<V> promise) {
        return supplyAsync(() -> state.getAndRemove(key), promise);
    }

    @Override
    public EventStage<Void> command(K key, C command, EventPromise<Void> promise) {
        return runAsync(() -> state.command(key, command), promise);
    }

    @Override
    public EventStage<V> getAndCommand(K key, C command, EventPromise<V> promise) {
        return supplyAsync(() -> state.commandAndGet(key, command), promise);
    }

    @Override
    public EventStage<V> commandAndGet(K key, C command, EventPromise<V> promise) {
        return supplyAsync(() -> state.commandAndGet(key, command), promise);
    }

    @Override
    public EventStage<List<V>> getBatch(List<K> keys, EventPromise<List<V>> promise) {
        return supplyAsync(() -> state.getBatch(keys), promise);
    }

    @Override
    public EventStage<Void> setBatch(List<K> keys, List<V> values, EventPromise<Void> promise) {
        return runAsync(() -> state.setBatch(keys, values), promise);
    }

    @Override
    public EventStage<List<V>> getAndSetBatch(List<K> keys, List<V> values,
                                              EventPromise<List<V>> promise) {
        return supplyAsync(() -> state.getAndSetBatch(keys, values), promise);
    }

    @Override
    public EventStage<Void> removeBatch(List<K> keys, EventPromise<Void> promise) {
        return runAsync(() -> state.removeBatch(keys), promise);
    }

    @Override
    public EventStage<List<V>> getAndRemoveBatch(List<K> keys, EventPromise<List<V>> promise) {
        return supplyAsync(() -> state.getAndRemoveBatch(keys), promise);
    }

    @Override
    public EventStage<Void> commandBatch(List<K> keys, C command, EventPromise<Void> promise) {
        return runAsync(() -> state.commandBatch(keys, command), promise);
    }

    @Override
    public EventStage<List<V>> getAndCommandBatch(List<K> keys, C command,
                                                  EventPromise<List<V>> promise) {
        return supplyAsync(() -> state.getAndCommandBatch(keys, command), promise);
    }

    @Override
    public EventStage<List<V>> commandAndGetBatch(List<K> keys, C command,
                                                  EventPromise<List<V>> promise) {
        return supplyAsync(() -> state.commandAndGetBatch(keys, command), promise);
    }
}
