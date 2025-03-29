package io.masterkun.axor.cluster.membership;

import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.cluster.ClusterEvent;

import java.util.Arrays;
import java.util.List;

public sealed interface MembershipMessage permits Gossip, MembershipCommand,
        MembershipMessage.AddListener, MembershipMessage.RemoveListener,
        MembershipMessage.UpdateMetaInfo {
    MembershipMessage JOIN = MembershipCommand.JOIN;
    MembershipMessage LEAVE = MembershipCommand.LEAVE;
    MembershipMessage FORCE_LEAVE = MembershipCommand.FORCE_LEAVE;

    static MembershipMessage addListener(ActorRef<ClusterEvent> listener) {
        return new AddListener(listener, false);
    }

    static MembershipMessage addListener(ActorRef<ClusterEvent> listener, boolean listenIncrement) {
        return new AddListener(listener, listenIncrement);
    }

    static MembershipMessage removeListener(ActorRef<ClusterEvent> listener) {
        return new RemoveListener(listener);
    }

    static MembershipMessage updateMetaInfo(MetaKey.Action... actions) {
        return new UpdateMetaInfo(Arrays.asList(actions.clone()));
    }

    record AddListener(ActorRef<ClusterEvent> listener,
                       boolean listenIncrement) implements MembershipMessage {
    }

    record RemoveListener(ActorRef<ClusterEvent> listener) implements MembershipMessage {
    }

    record UpdateMetaInfo(List<MetaKey.Action> actions) implements MembershipMessage {

    }

}
