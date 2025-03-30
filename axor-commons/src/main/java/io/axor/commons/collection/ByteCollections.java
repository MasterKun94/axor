/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.axor.commons.collection;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Utilities for byte-based primitive collections.
 */
public final class ByteCollections {

    private static final ByteObjectMap<Object> EMPTY_MAP = new EmptyMap();

    private ByteCollections() {
    }

    /**
     * Returns an unmodifiable empty {@link ByteObjectMap}.
     */
    @SuppressWarnings("unchecked")
    public static <V> ByteObjectMap<V> emptyMap() {
        return (ByteObjectMap<V>) EMPTY_MAP;
    }

    /**
     * Creates an unmodifiable wrapper around the given map.
     */
    public static <V> ByteObjectMap<V> unmodifiableMap(final ByteObjectMap<V> map) {
        if (map instanceof UnmodifiableMap) {
            return map;
        }
        return new UnmodifiableMap<>(map);
    }

    /**
     * An empty map. All operations that attempt to modify the map are unsupported.
     */
    public static final class EmptyMap implements ByteObjectMap<Object> {
        @Override
        public Object get(byte key) {
            return null;
        }

        @Override
        public Object put(byte key, Object value) {
            throw new UnsupportedOperationException("put");
        }

        @Override
        public Object remove(byte key) {
            return null;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean containsKey(Object key) {
            return false;
        }

        @Override
        public void clear() {
            // Do nothing.
        }

        @Override
        public Set<Byte> keySet() {
            return Collections.emptySet();
        }

        @Override
        public boolean containsKey(byte key) {
            return false;
        }

        @Override
        public boolean containsValue(Object value) {
            return false;
        }

        @Override
        public Iterable<PrimitiveEntry<Object>> entries() {
            return Collections.emptySet();
        }

        @Override
        public Object get(Object key) {
            return null;
        }

        @Override
        public Object put(Byte key, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object remove(Object key) {
            return null;
        }

        @Override
        public void putAll(Map<? extends Byte, ?> m) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<Object> values() {
            return Collections.emptyList();
        }

        @Override
        public Set<Entry<Byte, Object>> entrySet() {
            return Collections.emptySet();
        }

        @Override
        public String toString() {
            return "{}";
        }
    }

    /**
     * An unmodifiable wrapper around a {@link ByteObjectMap}.
     *
     * @param <V> the value type stored in the map.
     */
    public static final class UnmodifiableMap<V> implements ByteObjectMap<V> {
        private final ByteObjectMap<V> map;
        private Set<Byte> keySet;
        private Set<Entry<Byte, V>> entrySet;
        private Collection<V> values;
        private Iterable<PrimitiveEntry<V>> entries;

        UnmodifiableMap(ByteObjectMap<V> map) {
            this.map = map;
        }

        @Override
        public V get(byte key) {
            return map.get(key);
        }

        @Override
        public V put(byte key, V value) {
            throw new UnsupportedOperationException("put");
        }

        @Override
        public V remove(byte key) {
            throw new UnsupportedOperationException("remove");
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("clear");
        }

        @Override
        public boolean containsKey(byte key) {
            return map.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return map.containsValue(value);
        }

        @Override
        public boolean containsKey(Object key) {
            return map.containsKey(key);
        }

        @Override
        public V get(Object key) {
            return map.get(key);
        }

        @Override
        public V put(Byte key, V value) {
            throw new UnsupportedOperationException("put");
        }

        @Override
        public V remove(Object key) {
            throw new UnsupportedOperationException("remove");
        }

        @Override
        public void putAll(Map<? extends Byte, ? extends V> m) {
            throw new UnsupportedOperationException("putAll");
        }

        @Override
        public Iterable<PrimitiveEntry<V>> entries() {
            if (entries == null) {
                entries = () -> new IteratorImpl(map.entries().iterator());
            }

            return entries;
        }

        @Override
        public Set<Byte> keySet() {
            if (keySet == null) {
                keySet = Collections.unmodifiableSet(map.keySet());
            }
            return keySet;
        }

        @Override
        public Set<Entry<Byte, V>> entrySet() {
            if (entrySet == null) {
                entrySet = Collections.unmodifiableSet(map.entrySet());
            }
            return entrySet;
        }

        @Override
        public Collection<V> values() {
            if (values == null) {
                values = Collections.unmodifiableCollection(map.values());
            }
            return values;
        }

        @Override
        public int hashCode() {
            return map.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            UnmodifiableMap<?> that = (UnmodifiableMap<?>) o;
            return Objects.equals(map, that.map);
        }

        @Override
        public String toString() {
            return map.toString();
        }

        /**
         * Unmodifiable wrapper for an iterator.
         */
        private class IteratorImpl implements Iterator<PrimitiveEntry<V>> {
            final Iterator<PrimitiveEntry<V>> iter;

            IteratorImpl(Iterator<PrimitiveEntry<V>> iter) {
                this.iter = iter;
            }

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public PrimitiveEntry<V> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return new EntryImpl(iter.next());
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove");
            }
        }

        /**
         * Unmodifiable wrapper for an entry.
         */
        private class EntryImpl implements PrimitiveEntry<V> {
            private final PrimitiveEntry<V> entry;

            EntryImpl(PrimitiveEntry<V> entry) {
                this.entry = entry;
            }

            @Override
            public byte key() {
                return entry.key();
            }

            @Override
            public V value() {
                return entry.value();
            }

            @Override
            public void setValue(V value) {
                throw new UnsupportedOperationException("setValue");
            }
        }
    }
}
