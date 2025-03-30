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
    public final boolean ALIVE;
    public final boolean SERVABLE;

    MemberState(MemberAction bindAction, boolean alive, boolean serve) {
        BIND_ACTION = bindAction;
        ALIVE = alive;
        SERVABLE = serve;
    }
}
