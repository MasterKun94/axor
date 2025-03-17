package io.masterkun.kactor.cluster.pubsub;

import io.masterkun.kactor.cluster.Cluster;
import io.masterkun.kactor.runtime.MsgType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Pubsub<T> {
    private static final Map<Key, Pubsub<?>> cache = new ConcurrentHashMap<>();

    private final String topic;
    private final MsgType<T> msgType;
    private final Cluster cluster;

    public Pubsub(String topic, MsgType<T> msgType, Cluster cluster) {
        this.topic = topic;
        this.msgType = msgType;
        this.cluster = cluster;
    }

    @SuppressWarnings("unchecked")
    public static <T> Pubsub<T> getOrCreate(String topic, MsgType<T> msgType, Cluster cluster) {
        return (Pubsub<T>) cache.computeIfAbsent(new Key(topic, msgType, cluster.name()),
                k -> new Pubsub<>(k.topic, k.msgType, cluster));
    }

    private record Key(String topic, MsgType<?> msgType, String clusterName) {
    }
}
