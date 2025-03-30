package io.axor.runtime.impl;

import io.axor.runtime.EventDispatcher;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.StreamDefinition;
import io.axor.runtime.StreamInChannel;
import io.axor.runtime.StreamOutChannel;
import io.axor.runtime.StreamServer;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class NoopStreamServer implements StreamServer {
    private final SerdeRegistry serdeRegistry;
    private final String system;
    private volatile boolean closed;

    public NoopStreamServer(SerdeRegistry serdeRegistry, String system) {
        this.serdeRegistry = serdeRegistry;
        this.system = system;
    }

    @Override
    public Closeable register(StreamInChannel<?> channel, EventDispatcher executor) {
        return () -> {
        };
    }

    @Override
    public <T> StreamOutChannel<T> get(StreamDefinition<T> definition, EventDispatcher executor) {
        return new NoopStreamOutChannel<>(definition);
    }

    @Override
    public SerdeRegistry serdeRegistry() {
        return serdeRegistry;
    }

    @Override
    public String protocol() {
        return "noop";
    }

    @Override
    public int bindPort() {
        return 0;
    }

    @Override
    public StreamServer start() throws IOException {
        return this;
    }

    @Override
    public CompletableFuture<Void> shutdownAsync() {
        closed = true;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isShutdown() {
        return closed;
    }

    @Override
    public boolean isTerminated() {
        return closed;
    }
}
