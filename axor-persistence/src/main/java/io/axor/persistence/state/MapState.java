package io.axor.persistence.state;

import java.util.List;

public interface MapState<K, V, C> {
    V get(K key);

    void set(K key, V value);

    V getAndSet(K key, V value);

    void remove(K key);

    V getAndRemove(K key);

    void command(K key, C command);

    V getAndCommand(K key, C command);

    V commandAndGet(K key, C command);

    List<V> getBatch(List<K> keys);

    void setBatch(List<K> keys, List<V> values);

    List<V> getAndSetBatch(List<K> keys, List<V> values);

    void removeBatch(List<K> keys);

    List<V> getAndRemoveBatch(List<K> keys);

    void commandBatch(List<K> keys, C command);

    List<V> getAndCommandBatch(List<K> keys, C command);

    List<V> commandAndGetBatch(List<K> keys, C command);


}
