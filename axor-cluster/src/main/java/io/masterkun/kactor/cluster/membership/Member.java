package io.masterkun.kactor.cluster.membership;

import io.masterkun.kactor.api.ActorRef;

public record Member(long uid, MetaInfo metaInfo, ActorRef<? super Gossip> actor) {
    public boolean metaEquals(Member member) {
        return metaInfo.equals(member.metaInfo());
    }

    public Member metaTransform(MetaKey.Action... actions) {
        return new Member(uid, metaInfo.transform(actions), actor);
    }

    public Member metaTransform(Iterable<MetaKey.Action> actions) {
        return new Member(uid, metaInfo.transform(actions), actor);
    }

    @Override
    public String toString() {
        return "Member[" +
                "uid=" + uid +
                ", metaInfo=" + metaInfo +
                ", actor=" + actor +
                ']';
    }
}
