package io.masterkun.axor.runtime.stream.grpc;

import io.masterkun.axor.runtime.EventDispatcher;
import io.masterkun.axor.runtime.Metric;
import io.masterkun.axor.runtime.StreamDefinition;
import io.masterkun.axor.runtime.StreamInChannel;
import io.micrometer.core.instrument.Gauge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceRegistry {
    private final Map<String, StreamInChannel<?>> channels = new ConcurrentHashMap<>();
    private final Map<String, EventDispatcher> executors = new ConcurrentHashMap<>();

    public ServiceRegistry(String system) {
        Gauge.builder("axor.server.grpc.registered.services", channels, Map::size)
                .tag("system", system)
                .register(Metric.registry());
    }

    public UnregisterHook register(StreamInChannel<?> channel, EventDispatcher executor) {
        String name = channel.getSelfDefinition().address().method();
        channels.put(name, channel);
        executors.put(name, executor);
        return () -> {
            channels.remove(name);
            executors.remove(name);
        };
    }

    public StreamInChannel<?> getChannel(StreamDefinition<?> definition) {
        return channels.get(definition.address().method());
    }

    public EventDispatcher getExecutor(StreamDefinition<?> definition) {
        return executors.get(definition.address().method());
    }

    public interface UnregisterHook {
        void unregister();
    }
}
