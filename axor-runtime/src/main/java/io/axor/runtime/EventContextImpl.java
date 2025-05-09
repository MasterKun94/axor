package io.axor.runtime;

import io.axor.commons.collection.IntObjectHashMap;
import io.axor.commons.collection.IntObjectMap;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * An implementation of the {@link EventContext} interface that provides a mechanism for managing
 * and manipulating contextual data in an event-driven system. This class uses an internal map to
 * store key-value pairs, where keys are unique identifiers and values are objects that can be
 * marshalled and unmarshalled to and from a byte array.
 *
 * <p>The class supports adding, removing, and retrieving entries from the context, as well as
 * creating new scopes for the context.
 */
record EventContextImpl(IntObjectMap<BytesHolder> map, int propagateCnt) implements EventContext {
    static final ThreadLocal<EventContext> FALLBACK_CTX_TL = ThreadLocal
            .withInitial(() -> EventContext.INITIAL);

    public EventContextImpl(IntObjectMap<BytesHolder> map) {
        this(map, 0);
    }

    public EventContext propagate() {
        return new EventContextImpl(map, propagateCnt + 1);
    }

    @Override
    public <T> @Nullable T get(Key<T> key) {
        BytesHolder holder = map.get(key.id());
        return holder == null || holder.isHidden(propagateCnt) ? null :
                key.marshaller().read(holder.b, holder.off, holder.len);
    }

    @Override
    public <T> EventContext with(Key<T> key, T value, int propagateLevel) {
        if (propagateLevel < -1) {
            throw new IllegalArgumentException("propagateLevel must greater than or equal to -1");
        }
        IntObjectMap<BytesHolder> map = new IntObjectHashMap<>(this.map.size() + 1);
        map.putAll(this.map);
        map.put(key.id(), new BytesHolder(key.marshaller().write(value), propagateLevel));
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
        if (Thread.currentThread() instanceof EventDispatcher.DispatcherThread ext) {
            EventContext prev = ext.setContext(this);
            return prev == EventContext.INITIAL ?
                    () -> ext.setContext(EventContext.INITIAL) :
                    () -> ext.setContext(prev);
        }
        EventContext prev = FALLBACK_CTX_TL.get();
        FALLBACK_CTX_TL.set(this);
        return prev == EventContext.INITIAL ?
                () -> FALLBACK_CTX_TL.set(EventContext.INITIAL) :
                () -> FALLBACK_CTX_TL.set(prev);
    }

    record BytesHolder(byte[] b, int off, int len, int propagateLevel) {
        BytesHolder(byte[] b, int propagateLevel) {
            this(b, 0, b.length, propagateLevel);
        }

        BytesHolder(byte[] b) {
            this(b, 0, b.length, -1);
        }

        private boolean isHidden(int propagateCnt) {
            return propagateLevel != -1 && propagateLevel < propagateCnt;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            BytesHolder holder = (BytesHolder) o;
            return Arrays.equals(b, off, len + off,
                    holder.b, holder.off, holder.len + holder.off);
        }

        @Override
        public int hashCode() {
            return ByteBuffer.wrap(b, off, len).hashCode();
        }

    }
}
