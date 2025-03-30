package io.axor.cluster;

import io.axor.cluster.membership.MetaInfo;

public sealed interface ClusterEvent {

    sealed interface MemberEvent extends ClusterEvent {
        ClusterMember member();
    }

    record LocalStateChange(LocalMemberState state) implements ClusterEvent {
    }

    record LocalMemberStopped() implements ClusterEvent {
    }

    record MemberStateChanged(ClusterMember member,
                              MemberState state,
                              MemberState previousState) implements MemberEvent {
    }

    record MemberMetaInfoChanged(ClusterMember member,
                                 MetaInfo previousMetaInfo) implements MemberEvent {
    }
}
