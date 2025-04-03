package io.axor.persistence.state;

import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

public class AsyncValueStateSyncAdaptor<V, C> implements AsyncValueState<V, C> {
    private final Executor taskExecutor;
    private final ValueState<V, C> state;

    public AsyncValueStateSyncAdaptor(Executor taskExecutor, ValueState<V, C> state) {
        this.taskExecutor = taskExecutor;
        this.state = state;
    }

    private EventStage<V> supplyAsync(Callable<V> callable, EventPromise<V> promise) {
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
    public EventStage<V> get(EventPromise<V> promise) {
        return supplyAsync(state::get, promise);
    }

    @Override
    public EventStage<Void> set(V value, EventPromise<Void> promise) {
        return runAsync(() -> state.set(value), promise);
    }

    @Override
    public EventStage<V> getAndSet(V value, EventPromise<V> promise) {
        return supplyAsync(() -> state.getAndSet(value), promise);
    }

    @Override
    public EventStage<Void> remove(EventPromise<Void> promise) {
        return runAsync(state::remove, promise);
    }

    @Override
    public EventStage<V> getAndRemove(EventPromise<V> promise) {
        return supplyAsync(state::getAndRemove, promise);
    }

    @Override
    public EventStage<Void> command(C command, EventPromise<Void> promise) {
        return runAsync(() -> state.command(command), promise);
    }

    @Override
    public EventStage<V> getAndCommand(C command, EventPromise<V> promise) {
        return supplyAsync(() -> state.getAndCommand(command), promise);
    }

    @Override
    public EventStage<V> commandAndGet(C command, EventPromise<V> promise) {
        return supplyAsync(() -> state.commandAndGet(command), promise);
    }
}
