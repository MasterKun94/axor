package io.masterkun.axor.cluster.singleton;

import io.masterkun.axor.api.Signal;

enum StateChange implements Signal {
    SERVABLE,
    UNSERVABLE,
    LEADER_ADDED,
    LEADER_REMOVED,
    LEADER_CHANGED,
    INSTANCE_STARTED,
    INSTANCE_STOPPED,
}
