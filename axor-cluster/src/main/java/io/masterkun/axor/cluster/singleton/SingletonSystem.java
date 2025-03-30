package io.masterkun.axor.cluster.singleton;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.masterkun.axor.api.ActorCreator;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.InternalSignals;
import io.masterkun.axor.api.Signal;
import io.masterkun.axor.cluster.Cluster;
import io.masterkun.axor.commons.config.ConfigMapper;
import io.masterkun.axor.runtime.MsgType;

public class SingletonSystem {
    private final Cluster cluster;

    public SingletonSystem(Cluster cluster) {
        this.cluster = cluster;
    }

    public static SingletonSystem get(Cluster cluster) {
        return new SingletonSystem(cluster);
    }

    public <T> ActorRef<T> getOrStart(ActorCreator<T> creator, MsgType<T> msgType,
                                      Signal stopSignal, String name) {
        Config config;
        if (cluster.config().extraConfig().hasPath("singleton." + name)) {
            config = cluster.config().extraConfig().getConfig("singleton." + name);
        } else {
            config = ConfigFactory.empty();
        }
        SingletonConfig singletonConfig = ConfigMapper.map(config, SingletonConfig.class);
        String proxyName = "cluster/singleton/" + name + "/proxy";
        return cluster.system().getOrStart(c -> new ClusterSingletonProxy<>(
                cluster.name(), name, singletonConfig, stopSignal, msgType, c, creator), proxyName);
    }

    public <T> ActorRef<T> getOrStart(ActorCreator<T> creator, MsgType<T> msgType, String name) {
        return getOrStart(creator, msgType, InternalSignals.POISON_PILL, name);
    }
}
