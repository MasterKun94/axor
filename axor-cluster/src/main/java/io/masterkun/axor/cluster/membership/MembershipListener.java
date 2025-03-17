package io.masterkun.axor.cluster.membership;

import io.masterkun.axor.cluster.LocalMemberState;

public interface MembershipListener extends MemberManager.Listener {
    default void onLocalStateChange(LocalMemberState currentState) {
    }

    ;
}
