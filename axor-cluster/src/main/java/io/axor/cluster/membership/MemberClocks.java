package io.axor.cluster.membership;

public final class MemberClocks extends MessageIterable<MemberClock> {
    public static final MemberClocks EMPTY = new MemberClocks();

    MemberClocks(MemberClock... elems) {
        super(elems);
    }
}
