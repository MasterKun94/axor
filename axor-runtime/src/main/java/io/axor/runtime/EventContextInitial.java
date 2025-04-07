package io.axor.runtime;

import io.axor.commons.collection.IntObjectHashMap;
import io.axor.commons.collection.IntObjectMap;
import io.axor.commons.concurrent.EventStage;
import io.axor.runtime.EventContextImpl.BytesHolder;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;

/**
 * The {@code EventContextInitial} class is a final implementation of the {@link EventContext}
 * interface, representing an initial or empty event context. It provides methods to manage and
 * manipulate the context, including adding and removing key-value pairs, opening scopes, and
 * executing runnables and callables asynchronously.
 */
final class EventContextInitial implements EventContext {

    EventContextInitial() {
    }

    @Override
    public EventContext propagate() {
        return this;
    }

    @Override
    public <T> @Nullable T get(Key<T> key) {
        return null;
    }

    @Override
    public <T> EventContext with(Key<T> key, T value, int propagateLevel) {
        IntObjectMap<BytesHolder> map = new IntObjectHashMap<>(1);
        map.put(key.id(), new BytesHolder(key.marshaller().write(value), propagateLevel));
        return new EventContextImpl(map);
    }

    @Override
    public EventContext without(Key<?> key) {
        return this;
    }

    @Override
    public Scope openScope() {
        return () -> {
        };
    }

    @Override
    public void execute(Runnable runnable, EventDispatcher dispatcher) {
        dispatcher.execute(runnable);
    }

    @Override
    public EventStage<Void> runAsync(Runnable runnable, EventDispatcher dispatcher) {
        return EventStage.runAsync(runnable, dispatcher);
    }

    @Override
    public <T> EventStage<T> supplyAsync(Callable<T> callable, EventDispatcher dispatcher) {
        return EventStage.supplyAsync(callable, dispatcher);
    }
}
