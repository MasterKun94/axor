package io.axor.cluster;

import io.axor.cluster.membership.MemberAction;

public enum MemberState {
    NONE(null, false, false),
    UP(MemberAction.UPDATE, true, true),
    SUSPICIOUS(MemberAction.SUSPECT, true, true),
    DOWN(MemberAction.STRONG_SUSPECT, false, true),
    LEFT(MemberAction.LEAVE, false, false),
    REMOVED(MemberAction.REMOVE, false, false);
    public final MemberAction BIND_ACTION;
    public final boolean servable;
    public final boolean inCluster;

    MemberState(MemberAction bindAction, boolean servable, boolean alive) {
        BIND_ACTION = bindAction;
        this.servable = servable;
        inCluster = alive;
    }

    public boolean isServable() {
        return servable;
    }

    public boolean isInCluster() {
        return inCluster;
    }
}
