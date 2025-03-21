package io.masterkun.axor.runtime.impl;

import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.Serde;
import io.masterkun.axor.runtime.SerdeFactory;
import io.masterkun.axor.runtime.SerdeRegistry;

import java.io.IOException;
import java.io.InputStream;

public class NoopSerdeFactory implements SerdeFactory {
    public static final String NAME = "noop";
    private final SerdeRegistry registry;

    public NoopSerdeFactory(SerdeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean support(MsgType<?> type) {
        return true;
    }

    @Override
    public <T> Serde<T> create(MsgType<T> type) {
        return new NoopSerde<>(type);
    }

    @Override
    public String getImpl() {
        return NAME;
    }

    @Override
    public SerdeRegistry getSerdeRegistry() {
        return registry;
    }

    public static class NoopSerde<T> implements Serde<T> {
        private final MsgType<T> type;

        public NoopSerde(MsgType<T> type) {
            this.type = type;
        }

        @Override
        public InputStream serialize(T object) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public T deserialize(InputStream stream) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public MsgType<T> getType() {
            return type;
        }

        @Override
        public String getImpl() {
            return NAME;
        }
    }
}
