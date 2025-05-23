package io.axor.cluster.membership;

import io.axor.api.ActorRef;

public record MemberEvent(Member member, MemberAction action, VectorClock clock) {
    public long uid() {
        return member.uid();
    }

    public MetaInfo metaInfo() {
        return member.metaInfo();
    }

    public ActorRef<? super Gossip> actor() {
        return member.actor();
    }

    @Override
    public String toString() {
        return "MemberEvent[" +
               "uid=" + uid() +
               ", metaInfo=" + metaInfo() +
               ", actor=" + actor() +
               ", action=" + action +
               ']';
    }
}
