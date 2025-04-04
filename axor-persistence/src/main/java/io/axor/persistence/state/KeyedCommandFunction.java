package io.axor.persistence.state;

public interface KeyedCommandFunction<K, V, C> {
    V apply(K key, V value, C command);
}
