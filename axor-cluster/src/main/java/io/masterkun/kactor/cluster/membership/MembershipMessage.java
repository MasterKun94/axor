package io.masterkun.kactor.cluster.membership;

import java.util.Arrays;
import java.util.List;

public sealed interface MembershipMessage permits Gossip, MembershipCommand,
        MembershipMessage.AddListener, MembershipMessage.RemoveListener,
        MembershipMessage.UpdateMetaInfo {
    MembershipMessage JOIN = MembershipCommand.JOIN;
    MembershipMessage LEAVE = MembershipCommand.LEAVE;
    MembershipMessage FORCE_LEAVE = MembershipCommand.FORCE_LEAVE;

    static MembershipMessage addListener(MembershipListener listener) {
        return new AddListener(listener, false);
    }

    static MembershipMessage addListener(MembershipListener listener, boolean listenIncrement) {
        return new AddListener(listener, listenIncrement);
    }

    static MembershipMessage removeListener(MembershipListener listener) {
        return new RemoveListener(listener);
    }

    static MembershipMessage updateMetaInfo(MetaKey.Action... actions) {
        return new UpdateMetaInfo(Arrays.asList(actions.clone()));
    }

    record AddListener(MembershipListener listener,
                       boolean listenIncrement) implements MembershipMessage {
    }

    record RemoveListener(MembershipListener listener) implements MembershipMessage {
    }

    record UpdateMetaInfo(List<MetaKey.Action> actions) implements MembershipMessage {

    }

}
