package io.masterkun.axor.runtime.stream.grpc;

import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCallExecutorSupplier;
import io.masterkun.axor.runtime.DeadLetterHandlerFactory;
import io.masterkun.axor.runtime.EventDispatcher;
import io.masterkun.axor.runtime.LoggingDeadLetterHandlerFactory;
import io.masterkun.axor.runtime.SerdeRegistry;
import io.masterkun.axor.runtime.StreamDefinition;
import io.masterkun.axor.runtime.StreamInChannel;
import io.masterkun.axor.runtime.StreamOutChannel;
import io.masterkun.axor.runtime.StreamServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class GrpcStreamServer extends Server implements StreamServer {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcStreamServer.class);

    private final Server server;
    private final SerdeRegistry serdeRegistry;
    private final GrpcRuntime runtime;
    private final ServiceRegistry serviceRegistry;
    private final ChannelPool channelPool;

    public GrpcStreamServer(String system,
                            ServerBuilder<?> serverBuilder) {
        this(system, serverBuilder, SerdeRegistry.defaultInstance(),
                new LoggingDeadLetterHandlerFactory(LOG));
    }

    public GrpcStreamServer(String system,
                            ServerBuilder<?> serverBuilder,
                            SerdeRegistry serdeRegistry,
                            DeadLetterHandlerFactory deadLetterHandler) {
        this(system, serverBuilder, Duration.ofMinutes(10), serdeRegistry, deadLetterHandler);
    }

    public GrpcStreamServer(String system,
                            ServerBuilder<?> serverBuilder,
                            Duration keepAliveTimeout,
                            SerdeRegistry serdeRegistry,
                            DeadLetterHandlerFactory deadLetterHandler) {
        this(system, serverBuilder, null, keepAliveTimeout,
                (host, port) -> ManagedChannelBuilder
                        .forAddress(host, port)
                        .usePlaintext().build(), serdeRegistry, deadLetterHandler);
    }

    public GrpcStreamServer(String system,
                            ServerBuilder<?> serverBuilder,
                            ServerCallExecutorSupplier fallbackExecutorSupplier,
                            Duration keepAliveTimeout,
                            ChannelFactory channelFactory,
                            SerdeRegistry serdeRegistry,
                            DeadLetterHandlerFactory deadLetterHandler) {
        this.serdeRegistry = serdeRegistry;
        this.serviceRegistry = new ServiceRegistry(system);
        this.channelPool = new ChannelPool(channelFactory, keepAliveTimeout);
        this.runtime = new GrpcRuntime(system, serdeRegistry, serviceRegistry, channelPool,
                deadLetterHandler);
        var executorSupplier = new StreamChannelExecutorSupplier(runtime, serviceRegistry,
                fallbackExecutorSupplier);
        this.server = serverBuilder
                .addService(runtime.getServiceDefinition())
                .callExecutor(executorSupplier)
                .build();
    }

    @Override
    public Closeable register(StreamInChannel<?> channel, EventDispatcher executor) {
        var hook = serviceRegistry.register(channel, executor);
        return hook::unregister;
    }

    @Override
    public <T> StreamOutChannel<T> get(StreamDefinition<T> selfDefinition,
                                       EventDispatcher executor) {
        return new GrpcStreamOutChannel<>(runtime, selfDefinition, channelPool);
    }

    @Override
    public SerdeRegistry serdeRegistry() {
        return serdeRegistry;
    }

    @Override
    public String protocol() {
        return "grpc";
    }

    @Override
    public int bindPort() {
        return server.getPort();
    }

    @Override
    public GrpcStreamServer start() throws IOException {
        server.start();
        return this;
    }

    @Override
    public CompletableFuture<Void> shutdownAsync() {
        if (!server.isShutdown()) {
            channelPool.close();
            server.shutdown();
        }
        return CompletableFuture.runAsync(() -> {
            try {
                server.awaitTermination();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    public GrpcStreamServer shutdown() {
        channelPool.close();
        server.shutdown();
        return this;
    }

    @Override
    public GrpcStreamServer shutdownNow() {
        channelPool.close();
        server.shutdownNow();
        return this;
    }

    @Override
    public boolean isShutdown() {
        return server.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return server.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return server.awaitTermination(timeout, unit);
    }

    @Override
    public void awaitTermination() throws InterruptedException {
        server.awaitTermination();
    }

    public Server grpcServer() {
        return server;
    }
}
