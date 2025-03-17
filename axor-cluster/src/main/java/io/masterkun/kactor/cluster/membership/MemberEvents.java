package io.masterkun.kactor.cluster.membership;

public final class MemberEvents extends MessageIterable<MemberEvent> {
    public static final MemberEvents EMPTY = new MemberEvents();

    MemberEvents(MemberEvent... elems) {
        super(elems);
    }
}
