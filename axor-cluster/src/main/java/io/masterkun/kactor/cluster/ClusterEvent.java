package io.masterkun.kactor.cluster;

import io.masterkun.kactor.cluster.membership.MetaInfo;

public sealed interface ClusterEvent {

    sealed interface MemberEvent extends ClusterEvent {
        ClusterMember member();
    }

    record LocalStateChange(LocalMemberState state) implements ClusterEvent {
    }

    record MemberStateChanged(ClusterMember member,
                              MemberState state,
                              MemberState previousState) implements MemberEvent {
    }

    record MemberMetaInfoChanged(ClusterMember member,
                                 MetaInfo previousMetaInfo) implements MemberEvent {
    }
}
