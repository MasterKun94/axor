package io.axor.cluster.singleton;

import io.axor.runtime.Signal;

enum StateChange implements Signal {
    HEALTHY,
    UNHEALTHY,
    LEADER_ADDED,
    LEADER_REMOVED,
    LEADER_CHANGED,
    INSTANCE_STARTED,
    INSTANCE_STOPPED,
}
