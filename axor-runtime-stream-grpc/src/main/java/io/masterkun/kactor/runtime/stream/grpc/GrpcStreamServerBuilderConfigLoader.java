package io.masterkun.kactor.runtime.stream.grpc;

import com.typesafe.config.Config;
import io.grpc.InsecureServerCredentials;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.okhttp.OkHttpServerBuilder;
import io.grpc.protobuf.services.BinaryLogs;
import io.masterkun.kactor.runtime.Metric;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcClientInterceptor;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public sealed abstract class GrpcStreamServerBuilderConfigLoader
        <S extends ServerBuilder<?>, C extends ManagedChannelBuilder<?>> {

    private static final boolean NETTY_ENABLED;
    private static final Logger LOG = LoggerFactory.getLogger(GrpcStreamServerBuilderConfigLoader.class);

    static {
        boolean nettyEnabled = true;
        try {
            Class.forName("io.grpc.netty.NettyServerBuilder");
        } catch (ClassNotFoundException e) {
            nettyEnabled = false;
        }
        NETTY_ENABLED = nettyEnabled;
    }

    private final Config config;
    private final GrpcStreamServerBuilder builder;

    protected GrpcStreamServerBuilderConfigLoader(ServerBuilder<?> builder, Config config) {
        this.config = config;
        this.builder = new GrpcStreamServerBuilder(builder)
                .streamKeepAliveTimeout(Duration.ofMillis(config.getDuration("streamKeepAliveTimeout").toMillis()));
    }

    public static GrpcStreamServerBuilder load(Config config) {
        if (NETTY_ENABLED) {
            return NettyGrpcServerBuilderConfigLoader.create(config).load();
        } else {
            return OkHttpGrpcServerBuilderConfigLoader.create(config).load();
        }
    }

    public final GrpcStreamServerBuilder load() {
        for (var entry : config.root().entrySet()) {
            String key = entry.getKey();
            Config entryConfig = entry.getValue().atKey(key);
            serverBuild(builder, key, entryConfig);
        }
        builder.addChannelBuilderModifier(ManagedChannelBuilder::directExecutor);
        return builder;
    }

    protected abstract boolean doServerBuild(S builder, String key, Config entryConfig);

    private void serverBuild(GrpcStreamServerBuilder builder, String key, Config entryConfig) {
        //noinspection unchecked
        if (doServerBuild((S) builder.delegate(), key, entryConfig)) {
            return;
        }
        switch (key) {
            case "metricsEnabled" -> {
                if (entryConfig.getBoolean(key)) {
                    var serverInterceptor = new ObservationGrpcServerInterceptor(Metric.observationRegistry());
                    var clientInterceptor = new ObservationGrpcClientInterceptor(Metric.observationRegistry());
                    builder.intercept(serverInterceptor);
                    addModifier(builder1 -> builder1.intercept(clientInterceptor));
                }
            }
            case "keepAliveTime" -> {
                long time = entryConfig.getDuration(key).toMillis();
                builder.keepAliveTime(time, TimeUnit.MILLISECONDS);
            }
            case "keepAliveTimeout" -> {
                long time = entryConfig.getDuration(key).toMillis();
                builder.keepAliveTimeout(time, TimeUnit.MILLISECONDS);
            }
            case "permitKeepAliveTime" -> {
                long time = entryConfig.getDuration(key).toMillis();
                builder.permitKeepAliveTime(time, TimeUnit.MILLISECONDS);
            }
            case "permitKeepAliveWithoutCalls" -> {
                boolean enabled = entryConfig.getBoolean(key);
                builder.permitKeepAliveWithoutCalls(enabled);
            }
            case "handshakeTimeout" -> {
                long time = entryConfig.getDuration(key).toMillis();
                builder.handshakeTimeout(time, TimeUnit.MILLISECONDS);
            }
            case "maxConnectionIdle" -> {
                long time = entryConfig.getDuration(key).toMillis();
                builder.maxConnectionIdle(time, TimeUnit.MILLISECONDS);
            }
            case "maxConnectionAge" -> {
                long time = entryConfig.getDuration(key).toMillis();
                builder.maxConnectionAge(time, TimeUnit.MILLISECONDS);
            }
            case "maxConnectionAgeGrace" -> {
                long time = entryConfig.getDuration(key).toMillis();
                builder.maxConnectionAgeGrace(time, TimeUnit.MILLISECONDS);
            }
            case "maxInboundMessageSize" -> {
                int size = entryConfig.getInt(key);
                builder.maxInboundMessageSize(size);
            }
            case "maxInboundMetadataSize" -> {
                int sze = entryConfig.getInt(key);
                builder.maxInboundMetadataSize(sze);
            }
            case "directExecutor" -> {
                if (entryConfig.getBoolean(key)) {
                    builder.directExecutor();
                }
            }
            case "transportSecurity" -> {
                var sub = entryConfig.getConfig(key);
                var certChain = new File(sub.getString("certChain"));
                var privateKey = new File(sub.getString("privateKey"));
                builder.useTransportSecurity(certChain, privateKey);
            }
            case "binaryLog" -> {
                var sub = entryConfig.getConfig(key);
                var str = entryConfig.getString("logConfig");
                // TODO binaryLogConfig
                try {
                    builder.setBinaryLog(BinaryLogs.createBinaryLog());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            case "client" -> {
                for (var entry : entryConfig.getObject("client").entrySet()) {
                    var modifier = channelBuild(entry.getKey(), entry.getValue().atKey(key));
                    if (modifier != null) {
                        builder.addChannelBuilderModifier(modifier);
                    }
                }
            }
            default -> LOG.warn("Unknown grpc stream server option: {}={}", key, entryConfig);
        }
    }

    protected abstract ChannelBuilderModifier<C> doChannelBuild(String key, Config entryConfig);

    protected void addModifier(ChannelBuilderModifier<C> modifier) {
        if (modifier != null) {
            builder.addChannelBuilderModifier(modifier);
        }
    }

    private ChannelBuilderModifier<?> channelBuild(String key, Config entryConfig) {
        var ret = doChannelBuild(key, entryConfig);
        if (ret != null) {
            return ret;
        }
        switch (key) {
            case "perRpcBufferLimit" -> {
                long limit = entryConfig.getBytes(key);
                return (b -> b.perRpcBufferLimit(limit));
            }
            case "defaultLoadBalancingPolicy" -> {
                String policy = entryConfig.getString(key);
                return (b -> b.defaultLoadBalancingPolicy(policy));
            }
            case "enableRetry" -> {
                if (entryConfig.getBoolean(key)) {
                    return (ManagedChannelBuilder::enableRetry);
                } else {
                    return (ManagedChannelBuilder::disableRetry);
                }
            }
            case "retryBufferSize" -> {
                long size = entryConfig.getBytes(key);
                return b -> b.retryBufferSize(size);
            }
            case "maxRetryAttempts" -> {
                int num = entryConfig.getInt(key);
                return b -> b.maxRetryAttempts(num);
            }
            case "idleTimeout" -> {
                long time = entryConfig.getDuration(key).toMillis();
                return b -> b.idleTimeout(time, TimeUnit.MILLISECONDS);
            }
            case "keepAliveTimeout" -> {
                long time = entryConfig.getDuration(key).toMillis();
                return b -> b.keepAliveTimeout(time, TimeUnit.MILLISECONDS);
            }
            case "keepAliveTime" -> {
                long time = entryConfig.getDuration(key).toMillis();
                return b -> b.keepAliveTime(time, TimeUnit.MILLISECONDS);
            }
            case "keepAliveWithoutCalls" -> {
                boolean enabled = entryConfig.getBoolean(key);
                return b -> b.keepAliveWithoutCalls(enabled);
            }
            case "maxHedgedAttempts" -> {
                int num = entryConfig.getInt(key);
                return b -> b.maxHedgedAttempts(num);
            }
            case "maxInboundMessageSize" -> {
                int size = entryConfig.getInt(key);
                return b -> b.maxInboundMessageSize(size);
            }
            case "maxInboundMetadataSize" -> {
                int size = entryConfig.getInt(key);
                return b -> b.maxInboundMetadataSize(size);
            }
            case "maxTraceEvents" -> {
                int size = entryConfig.getInt(key);
                return b -> b.maxTraceEvents(size);
            }
            default -> {
                LOG.warn("Unknown grpc stream client option: {}={}", key, entryConfig);
                return null;
            }
        }
    }
}

final class NettyGrpcServerBuilderConfigLoader extends GrpcStreamServerBuilderConfigLoader<NettyServerBuilder, NettyChannelBuilder> {

    public NettyGrpcServerBuilderConfigLoader(ServerBuilder<?> builder, Config config) {
        super(builder, config);
    }

    public static NettyGrpcServerBuilderConfigLoader create(Config config) {
        int port = config.getInt("bind.port");
        String host = config.hasPath("bind.host") ? config.getString("bind.host") : null;
        NettyServerBuilder builder;
        if (host != null) {
            builder = NettyServerBuilder
                    .forAddress(new InetSocketAddress(host, port));
        } else {
            builder = NettyServerBuilder.forPort(port);
        }
        return new NettyGrpcServerBuilderConfigLoader(builder, config);
    }

    @Override
    protected boolean doServerBuild(NettyServerBuilder builder, String key, Config entryConfig) {
        switch (key) {
            case "maxConcurrentCallsPerConnection" -> {
                int num = entryConfig.getInt(key);
                builder.maxConcurrentCallsPerConnection(num);
            }
            case "initialFlowControlWindow" -> {
                int window = entryConfig.getInt(key);
                builder.initialFlowControlWindow(window);
                addModifier(b -> b.initialFlowControlWindow(window));
            }
            case "flowControlWindow" -> {
                int window = entryConfig.getInt(key);
                builder.flowControlWindow(window);
                addModifier(b -> b.flowControlWindow(window));
            }
            case "maxRstFramesPerWindow" -> {
                var sub = entryConfig.getConfig(key);
                builder.maxRstFramesPerWindow(
                        sub.getInt("maxRstStream"),
                        sub.getInt("secondsPerWindow"));
            }
        }
        return false;
    }

    @Override
    protected ChannelBuilderModifier<NettyChannelBuilder> doChannelBuild(String key, Config entryConfig) {
        switch (key) {
            case "initialFlowControlWindow" -> {
                int window = entryConfig.getInt(key);
                return b -> b.initialFlowControlWindow(window);
            }
            case "flowControlWindow" -> {
                int window = entryConfig.getInt(key);
                return b -> b.flowControlWindow(window);
            }
            default -> {
                return null;
            }
        }
    }
}

final class OkHttpGrpcServerBuilderConfigLoader extends GrpcStreamServerBuilderConfigLoader<OkHttpServerBuilder, OkHttpChannelBuilder> {

    public OkHttpGrpcServerBuilderConfigLoader(ServerBuilder<?> builder, Config config) {
        super(builder, config);
    }

    public static OkHttpGrpcServerBuilderConfigLoader create(Config config) {

        if (config.hasPath("transportSecurity")) {
            throw new UnsupportedOperationException("OKHTTP transport security is not supported");
        }
        int port = config.getInt("bind.port");
        String host = config.hasPath("bind.host") ? config.getString("bind.host") : null;
        ServerBuilder<?> builder;
        if (host != null) {
            builder = OkHttpServerBuilder.forPort(
                    new InetSocketAddress(host, port),
                    InsecureServerCredentials.create());
        } else {
            builder = OkHttpServerBuilder.forPort(port,
                    InsecureServerCredentials.create());
        }
        return new OkHttpGrpcServerBuilderConfigLoader(builder, config);
    }

    @Override
    protected boolean doServerBuild(OkHttpServerBuilder builder, String key, Config entryConfig) {
        switch (key) {
            case "maxConcurrentCallsPerConnection" -> {
                int num = entryConfig.getInt(key);
                builder.maxConcurrentCallsPerConnection(num);
            }
            case "flowControlWindow" -> {
                int window = entryConfig.getInt(key);
                builder.flowControlWindow(window);
                addModifier(b -> b.flowControlWindow(window));
            }
        }
        return false;
    }

    @Override
    protected ChannelBuilderModifier<OkHttpChannelBuilder> doChannelBuild(String key, Config entryConfig) {
        if (key.equals("flowControlWindow")) {
            int window = entryConfig.getInt(key);
            return b -> b.flowControlWindow(window);
        }
        return null;
    }

}
