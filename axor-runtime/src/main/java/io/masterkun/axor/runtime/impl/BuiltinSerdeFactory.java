package io.masterkun.axor.runtime.impl;

import io.masterkun.axor.runtime.AbstractSerdeFactory;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.Registry;
import io.masterkun.axor.runtime.Serde;
import io.masterkun.axor.runtime.SerdeRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BuiltinSerdeFactory extends AbstractSerdeFactory {
    private final Map<MsgType<?>, BuiltinSerde<?>> serdes;
    private volatile boolean initialized;

    public BuiltinSerdeFactory(SerdeRegistry registry) {
        super("builtin", registry);
        this.serdes = new ConcurrentHashMap<>();
    }

    private void initialize() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    for (var initializer : Registry.listAvailable(BuiltinSerdeFactoryInitializer.class)) {
                        initializer.initialize(this, getSerdeRegistry());
                    }
                    initialized = true;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Serde<T> register(MsgType<? extends T> type, BuiltinSerde<T> serde) {
        return (Serde<T>) serdes.put(type, serde);
    }

    public <T> Serde<T> register(Class<? extends T> type, BuiltinSerde<T> serde) {
        return register(MsgType.of(type), serde);
    }

    public <T> Serde<T> register(BuiltinSerde<T> serde) {
        return register(serde.getType(), serde);
    }

    @Override
    public boolean support(MsgType<?> type) {
        initialize();
        if (serdes.containsKey(type)) {
            return true;
        }
        for (MsgType<?> msgType : serdes.keySet()) {
            if (msgType.isSupport(type)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> BuiltinSerde<T> create(MsgType<T> type) {
        initialize();
        BuiltinSerde<?> serde = serdes.get(type);
        if (serde != null) {
            return (BuiltinSerde<T>) serde;
        }
        for (var entry : serdes.entrySet()) {
            if (entry.getKey().isSupport(type)) {
                return (BuiltinSerde<T>) entry.getValue();
            }
        }
        throw new IllegalArgumentException("Serde type " + type + " not supported");
    }
}
