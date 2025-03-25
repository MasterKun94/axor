package io.masterkun.axor.cluster.singleton;

import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.cluster.Cluster;

public class ClusterSingletonManager {
    private final String singletonName;
    private final ActorSystem system;
    private final Cluster cluster;

    public ClusterSingletonManager(String clusterName, String singletonName, ActorSystem system) {
        this.singletonName = singletonName;
        this.system = system;
        this.cluster = Cluster.get(clusterName, system);
    }
}
