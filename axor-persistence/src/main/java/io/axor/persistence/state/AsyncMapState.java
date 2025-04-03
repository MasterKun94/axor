package io.axor.persistence.state;

import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;

import java.util.List;

public interface AsyncMapState<K, V, C> {
    EventStage<V> get(K key, EventPromise<V> promise);

    EventStage<Void> set(K key, V value, EventPromise<Void> promise);

    EventStage<V> getAndSet(K key, V value, EventPromise<V> promise);

    EventStage<Void> remove(K key, EventPromise<Void> promise);

    EventStage<V> getAndRemove(K key, EventPromise<V> promise);

    EventStage<Void> command(K key, C command, EventPromise<Void> promise);

    EventStage<V> getAndCommand(K key, C command, EventPromise<V> promise);

    EventStage<V> commandAndGet(K key, C command, EventPromise<V> promise);

    EventStage<List<V>> getBatch(List<K> keys, EventPromise<List<V>> promise);

    EventStage<Void> setBatch(List<K> keys, List<V> values, EventPromise<Void> promise);

    EventStage<List<V>> getAndSetBatch(List<K> keys, List<V> values, EventPromise<List<V>> promise);

    EventStage<Void> removeBatch(List<K> keys, EventPromise<Void> promise);

    EventStage<List<V>> getAndRemoveBatch(List<K> keys, EventPromise<List<V>> promise);

    EventStage<Void> commandBatch(List<K> keys, C command, EventPromise<Void> promise);

    EventStage<List<V>> getAndCommandBatch(List<K> keys, C command, EventPromise<List<V>> promise);

    EventStage<List<V>> commandAndGetBatch(List<K> keys, C command, EventPromise<List<V>> promise);
}
