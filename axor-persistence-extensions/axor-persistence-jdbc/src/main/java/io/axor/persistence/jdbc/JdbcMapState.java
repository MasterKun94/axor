package io.axor.persistence.jdbc;

import com.typesafe.config.Config;
import io.axor.persistence.state.MapState;
import io.axor.persistence.state.MapStateDef;

import java.io.Closeable;
import java.util.List;

public class JdbcMapState<K, V, C> implements MapState<K, V, C>, Closeable {
    private final MapStateDef<K, V, C> def;
    private final JdbcStoreInstance<K, V> instance;
    private final boolean readOnly;

    JdbcMapState(MapStateDef<K, V, C> def) {
        Config config = Utils.resolveConfig(def.getConfig());
        JdbcStore store = JdbcStore.get(config);
        boolean multiWriter;
        switch (def.getScope()) {
            case PRIVATE -> {
                this.readOnly = false;
                multiWriter = false;
            }
            case READ -> {
                this.readOnly = true;
                multiWriter = true;
            }
            case READ_WRITE -> {
                this.readOnly = false;
                multiWriter = true;
            }
            case null, default ->
                    throw new IllegalArgumentException("Unknown scope: " + def.getScope());
        }
        this.instance = store.getInstance(def.getName(),
                def.getKeySerde(),
                def.getValueSerde(),
                multiWriter);
        this.def = def;
    }

    @Override
    public V get(K key) {
        return instance.get(key);
    }

    @Override
    public void set(K key, V value) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        instance.upsert(key, value);
    }

    @Override
    public V getAndSet(K key, V value) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        return instance.getAndUpsert(key, value);
    }

    @Override
    public void remove(K key) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        instance.delete(key);
    }

    @Override
    public V getAndRemove(K key) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        return instance.getAndDelete(key);
    }

    @Override
    public void command(K key, C command) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        instance.upsert(key, (k, v) -> def.getCommandFunction().apply(k, v, command));
    }

    @Override
    public V getAndCommand(K key, C command) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        return instance.getAndUpsert(key, (k, v) -> def.getCommandFunction().apply(k, v, command));
    }

    @Override
    public V commandAndGet(K key, C command) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        return instance.upsertAndGet(key, (k, v) -> def.getCommandFunction().apply(k, v, command));
    }

    @Override
    public List<V> getBatch(List<K> keys) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        return instance.getBatch(keys);
    }

    @Override
    public void setBatch(List<K> keys, List<V> values) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        instance.upsertBatch(keys, values);
    }

    @Override
    public List<V> getAndSetBatch(List<K> keys, List<V> values) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        return instance.getAndUpsertBatch(keys, values);
    }

    @Override
    public void removeBatch(List<K> keys) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        instance.deleteBatch(keys);
    }

    @Override
    public List<V> getAndRemoveBatch(List<K> keys) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        return instance.getAndDeleteBatch(keys);
    }

    @Override
    public void commandBatch(List<K> keys, C command) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        instance.upsertBatch(keys, (k, v) -> def.getCommandFunction().apply(k, v, command));
    }

    @Override
    public List<V> getAndCommandBatch(List<K> keys, C command) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        return instance.getAndUpsertBatch(keys, (k, v) -> def.getCommandFunction().apply(k, v,
                command));
    }

    @Override
    public List<V> commandAndGetBatch(List<K> keys, C command) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        return instance.upsertAndGetBatch(keys, (k, v) -> def.getCommandFunction().apply(k, v,
                command));
    }

    @Override
    public void close() {
    }
}
