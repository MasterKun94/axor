package io.axor.runtime.impl;

import io.axor.runtime.DeadLetterHandlerFactory;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.StreamServer;
import io.axor.runtime.StreamServerBuilder;

public class NoopStreamServerBuilder implements StreamServerBuilder {
    private String system;
    private SerdeRegistry serdeRegistry = SerdeRegistry.defaultInstance();

    @Override
    public StreamServerBuilder system(String system) {
        this.system = system;
        return this;
    }

    @Override
    public StreamServerBuilder serdeRegistry(SerdeRegistry serdeRegistry) {
        this.serdeRegistry = serdeRegistry;
        return this;
    }

    @Override
    public StreamServerBuilder deadLetterHandler(DeadLetterHandlerFactory deadLetterHandler) {
        return this;
    }

    @Override
    public StreamServer build() {
        return new NoopStreamServer(serdeRegistry, system);
    }
}
