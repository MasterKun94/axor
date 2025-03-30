package io.axor.runtime;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Defines the contract for a server that manages streaming communication channels. Implementations
 * of this interface are responsible for handling the registration of input channels, providing
 * output channels, and managing the lifecycle of the server.
 */
public interface StreamServer {

    Closeable register(StreamInChannel<?> channel, EventDispatcher executor);

    <T> StreamOutChannel<T> get(StreamDefinition<T> definition, EventDispatcher executor);

    SerdeRegistry serdeRegistry();

    String protocol();

    int bindPort();

    StreamServer start() throws IOException;

    CompletableFuture<Void> shutdownAsync();

    boolean isShutdown();

    boolean isTerminated();
}
