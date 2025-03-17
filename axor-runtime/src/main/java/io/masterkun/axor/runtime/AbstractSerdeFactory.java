package io.masterkun.axor.runtime;

public abstract class AbstractSerdeFactory implements SerdeFactory {
    private final String impl;
    private final SerdeRegistry registry;

    public AbstractSerdeFactory(String impl, SerdeRegistry registry) {
        this.impl = impl;
        this.registry = registry;
    }

    @Override
    public SerdeRegistry getSerdeRegistry() {
        return registry;
    }

    @Override
    public String getImpl() {
        return impl;
    }
}
