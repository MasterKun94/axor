package io.axor.persistence.jdbc;


import com.typesafe.config.Config;
import io.axor.persistence.state.ValueState;
import io.axor.persistence.state.ValueStateDef;

import java.io.Closeable;

public class JdbcValueState<V, C> implements ValueState<V, C>, Closeable {
    private final ValueStateDef<V, C> def;
    private final JdbcStoreInstance<String, V> instance;
    private final boolean readOnly;

    JdbcValueState(ValueStateDef<V, C> def) {
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
                new NoopSerde(),
                def.getValueSerde(),
                multiWriter);
        this.def = def;
    }

    @Override
    public V get() {
        return instance.get("");
    }

    @Override
    public void set(V value) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        instance.upsert("", value);
    }

    @Override
    public V getAndSet(V value) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        return instance.getAndUpsert("", value);
    }

    @Override
    public void remove() {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        instance.delete("");
    }

    @Override
    public V getAndRemove() {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        return instance.getAndDelete("");
    }

    @Override
    public void command(C command) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        instance.upsert("", (k, v) -> def.getCommandFunction().apply(v, command));
    }

    @Override
    public V getAndCommand(C command) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        return instance.getAndUpsert("", (k, v) -> def.getCommandFunction().apply(v, command));
    }

    @Override
    public V commandAndGet(C command) {
        if (readOnly) throw new IllegalArgumentException("state is read only");
        return instance.upsertAndGet("", (k, v) -> def.getCommandFunction().apply(v, command));
    }

    @Override
    public void close() {
    }
}
