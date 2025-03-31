package io.axor.runtime;

import io.axor.commons.collection.IntObjectHashMap;
import io.axor.commons.collection.IntObjectMap;
import org.jetbrains.annotations.Nullable;

/**
 * An implementation of the {@link EventContext} interface that provides a mechanism for managing
 * and manipulating contextual data in an event-driven system. This class uses an internal map to
 * store key-value pairs, where keys are unique identifiers and values are objects that can be
 * marshalled and unmarshalled to and from a byte array.
 *
 * <p>The class supports adding, removing, and retrieving entries from the context, as well as
 * creating new scopes for the context.
 */
final class EventContextImpl implements EventContext {
    private final IntObjectMap<BytesHolder> map;

    EventContextImpl(IntObjectMap<BytesHolder> map) {
        this.map = map;
    }

    @Override
    public <T> @Nullable T get(Key<T> key) {
        BytesHolder holder = map.get(key.id());
        return holder == null ? null : key.marshaller().read(holder.b, holder.off, holder.len);
    }

    @Override
    public <T> EventContext with(Key<T> key, T value) {
        IntObjectMap<BytesHolder> map = new IntObjectHashMap<>(this.map.size() + 1);
        map.putAll(this.map);
        map.put(key.id(), new BytesHolder(key.marshaller().write(value)));
        return new EventContextImpl(map);
    }

    @Override
    public EventContext without(Key<?> key) {
        if (map.size() == 1 && map.containsKey(key.id())) {
            return EventContext.INITIAL;
        }
        IntObjectMap<BytesHolder> map = new IntObjectHashMap<>(this.map.size());
        map.putAll(this.map);
        map.remove(key.id());
        return new EventContextImpl(map);
    }

    @Override
    public Scope openScope() {
        EventContext prev = EventContext.set(this);
        return () -> EventContext.set(prev);
    }

    record BytesHolder(byte[] b, int off, int len) {
        BytesHolder(byte[] b) {
            this(b, 0, b.length);
        }
    }
}
