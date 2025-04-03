package io.axor.persistence.jdbc;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;

public interface JdbcStoreInstance<K, V> {
    @Nullable
    V query(K key);

    void upsert(K key, @Nullable V value);

    void upsert(K key, BiFunction<K, @Nullable V, @Nullable V> valueFunc);

    @Nullable
    V getAndUpsert(K key, @Nullable V value);

    @Nullable
    V getAndUpsert(K key, BiFunction<K, @Nullable V, @Nullable V> valueFunc);

    @Nullable
    V upsertAndGet(K key, BiFunction<K, @Nullable V, @Nullable V> valueFunc);

    void delete(K key);

    @Nullable
    V getAndDelete(K key);

    List<@Nullable V> queryBatch(List<K> keys);

    void upsertBatch(List<K> keys, List<@Nullable V> values);

    void upsertBatch(List<K> keys, BiFunction<K, @Nullable V, @Nullable V> valueFunc);

    List<@Nullable V> getAndUpsertBatch(List<K> keys, List<@Nullable V> values);

    List<@Nullable V> getAndUpsertBatch(List<K> keys,
                                        BiFunction<K, @Nullable V, @Nullable V> valueFunc);

    List<@Nullable V> upsertAndGetBatch(List<K> keys,
                                        BiFunction<K, @Nullable V, @Nullable V> valueFunc);

    void deleteBatch(List<K> keys);

    List<@Nullable V> getAndDeleteBatch(List<K> keys);
}
