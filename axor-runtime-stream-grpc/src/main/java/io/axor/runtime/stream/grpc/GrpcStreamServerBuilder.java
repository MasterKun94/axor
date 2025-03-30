package io.axor.runtime.stream.grpc;

import com.typesafe.config.Config;
import io.axor.runtime.DeadLetterHandlerFactory;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.StreamServerBuilder;
import io.grpc.BinaryLog;
import io.grpc.ForwardingServerBuilder;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.grpc.ServerCallExecutorSupplier;

import java.io.File;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class GrpcStreamServerBuilder extends ForwardingServerBuilder<GrpcStreamServerBuilder> implements StreamServerBuilder {

    private final ServerBuilder<?> builder;
    private final List<ChannelBuilderModifier<?>> channelBuilderModifiers = new ArrayList<>();
    private String system;
    private boolean securityEnabled = false;
    private ServerCallExecutorSupplier fallbackExecutorSupplier;
    private Duration streamKeepAliveTimeout = Duration.ofMinutes(10);
    private SerdeRegistry serdeRegistry;
    private DeadLetterHandlerFactory deadLetterHandler;

    public GrpcStreamServerBuilder(ServerBuilder<?> builder) {
        this.builder = builder;
    }

    public static GrpcStreamServerBuilder forBuilder(ServerBuilder<?> builder) {
        return new GrpcStreamServerBuilder(builder);
    }

    public static GrpcStreamServerBuilder forServer(Config config) {
        return GrpcStreamServerBuilderConfigLoader.load(config);
    }

    @Override
    public StreamServerBuilder system(String system) {
        this.system = system;
        return this;
    }

    @Override
    public GrpcStreamServerBuilder setBinaryLog(BinaryLog binaryLog) {
        channelBuilderModifiers.add(channelBuilder ->
                channelBuilder.setBinaryLog(Objects.requireNonNull(binaryLog)));
        return super.setBinaryLog(binaryLog);
    }

    @Override
    public GrpcStreamServerBuilder keepAliveTime(long keepAliveTime, TimeUnit timeUnit) {
        channelBuilderModifiers.add(channelBuilder ->
                channelBuilder.keepAliveTime(keepAliveTime, timeUnit));
        return super.keepAliveTime(keepAliveTime, timeUnit);
    }

    @Override
    public GrpcStreamServerBuilder keepAliveTimeout(long keepAliveTimeout, TimeUnit timeUnit) {
        channelBuilderModifiers.add(channelBuilder ->
                channelBuilder.keepAliveTimeout(keepAliveTimeout, timeUnit));
        return super.keepAliveTimeout(keepAliveTimeout, timeUnit);
    }

    @Override
    public GrpcStreamServerBuilder maxInboundMessageSize(int bytes) {
        channelBuilderModifiers.add(channelBuilder ->
                channelBuilder.maxInboundMessageSize(bytes));
        return super.maxInboundMessageSize(bytes);
    }

    @Override
    public GrpcStreamServerBuilder maxInboundMetadataSize(int bytes) {
        channelBuilderModifiers.add(channelBuilder ->
                channelBuilder.maxInboundMetadataSize(bytes));
        return super.maxInboundMetadataSize(bytes);
    }

    @Override
    public GrpcStreamServerBuilder useTransportSecurity(InputStream certChain,
                                                        InputStream privateKey) {
        securityEnabled = true;
        return super.useTransportSecurity(certChain, privateKey);
    }

    @Override
    public GrpcStreamServerBuilder useTransportSecurity(File certChain, File privateKey) {
        securityEnabled = true;
        return super.useTransportSecurity(certChain, privateKey);
    }

    @Override
    protected ServerBuilder<?> delegate() {
        return builder;
    }

    @Override
    public GrpcStreamServerBuilder callExecutor(ServerCallExecutorSupplier executorSupplier) {
        this.fallbackExecutorSupplier = executorSupplier;
        return this;
    }

    public GrpcStreamServerBuilder streamKeepAliveTimeout(Duration streamKeepAliveTimeout) {
        Objects.requireNonNull(streamKeepAliveTimeout, "streamKeepAliveTimeout must not be null");
        this.streamKeepAliveTimeout = streamKeepAliveTimeout;
        return this;
    }

    public GrpcStreamServerBuilder addChannelBuilderModifier(ChannelBuilderModifier<?> modifier) {
        Objects.requireNonNull(modifier, "modifier must not be null");
        channelBuilderModifiers.add(modifier);
        return this;
    }

    @Override
    public GrpcStreamServerBuilder serdeRegistry(SerdeRegistry serdeRegistry) {
        Objects.requireNonNull(serdeRegistry, "serdeRegistry must not be null");
        this.serdeRegistry = serdeRegistry;
        return this;
    }

    @Override
    public StreamServerBuilder deadLetterHandler(DeadLetterHandlerFactory deadLetterHandler) {
        Objects.requireNonNull(deadLetterHandler, "deadLetterHandler must not be null");
        this.deadLetterHandler = deadLetterHandler;
        return this;
    }

    public ServerBuilder<?> getDelegateBuilder() {
        return builder;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public GrpcStreamServer build() {
        Objects.requireNonNull(system, "system must not be null");
        ChannelFactory factory = (host, port) -> {
            var builder = ManagedChannelBuilder.forAddress(host, port);
            if (securityEnabled) {
                builder.useTransportSecurity();
            } else {
                builder.usePlaintext();
            }
            for (ChannelBuilderModifier modifier : channelBuilderModifiers) {
                modifier.modify(builder);
            }
            return builder.directExecutor().build();
        };
        if (serdeRegistry == null) {
            throw new IllegalArgumentException("serdeRegistry must not be null");
        }
        if (deadLetterHandler == null) {
            throw new IllegalArgumentException("deadLetterHandler must not be null");
        }
        return new GrpcStreamServer(system, delegate(), fallbackExecutorSupplier,
                streamKeepAliveTimeout, factory, serdeRegistry, deadLetterHandler);
    }
}

