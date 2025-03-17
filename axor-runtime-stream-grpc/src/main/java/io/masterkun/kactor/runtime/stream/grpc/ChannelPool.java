package io.masterkun.kactor.runtime.stream.grpc;

import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.Channel;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ChannelPool implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(ChannelPool.class);
    private static final ScheduledExecutorService SCHEDULER = Executors
            .newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                    .setNameFormat("GrpcStreamKeepaliveManager-%d")
                    .setDaemon(true)
                    .build());
    private final Map<HostAndPort, ManagedChannel> channelPool = new ConcurrentHashMap<>();
    private final ChannelFactory channelFactory;
    private final Duration keepaliveTimeout;

    public ChannelPool(ChannelFactory channelFactory, Duration keepaliveTimeout) {
        this.channelFactory = channelFactory;
        this.keepaliveTimeout = keepaliveTimeout;
    }

    public Channel getChannel(HostAndPort address) {
        return channelPool.computeIfAbsent(address, k -> {
            ManagedChannel ch = channelFactory.createChannel(k.getHost(), k.getPort());
            ch.notifyWhenStateChanged(ConnectivityState.CONNECTING, new KeepaliveListener(k, ch));
            return ch;
        });
    }

    public void notifyAlive(HostAndPort address) {
        ManagedChannel channel = channelPool.get(address);
        if (channel != null && channel.getState(false).equals(ConnectivityState.TRANSIENT_FAILURE)) {
            channel.resetConnectBackoff();
        }
    }

    @Override
    public void close() {
        for (var entry : channelPool.entrySet()) {
            try {
                entry.getValue().shutdown();
            } catch (Exception e) {
                LOG.error("Error shutting down channel", e);
            }
        }
    }

    private class KeepaliveListener implements Runnable {
        private final HostAndPort address;
        private final ManagedChannel channel;
        private final AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

        private KeepaliveListener(HostAndPort address, ManagedChannel channel) {
            this.address = address;
            this.channel = channel;
        }

        @Override
        public void run() {
            ConnectivityState currentState = channel.getState(false);
            try {
                LOG.debug("Channel[{}] state is {}", address, currentState);
                ScheduledFuture<?> future;
                switch (currentState) {
                    case READY:
                        future = futureRef.getAndSet(null);
                        if (future != null) {
                            future.cancel(false);
                        }
                        break;
                    case IDLE:
                    case CONNECTING:
                    case TRANSIENT_FAILURE:
                        if (futureRef.get() != null) {
                            break;
                        }
                        future = SCHEDULER.schedule(() -> {
                            LOG.info("Channel state not ready over {}, start cleanup", keepaliveTimeout);
                            channelPool.remove(address, channel);
                            channel.shutdown();
                        }, keepaliveTimeout.toMillis(), TimeUnit.MILLISECONDS);
                        if (!futureRef.compareAndSet(null, future)) {
                            future.cancel(false);
                        }
                        break;
                    case SHUTDOWN:
                        future = futureRef.getAndSet(null);
                        if (future != null) {
                            future.cancel(false);
                        }
                        return;
                    default:
                        break;
                }
            } finally {
                channel.notifyWhenStateChanged(currentState, this);
            }
        }
    }

}
