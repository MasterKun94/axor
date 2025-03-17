package io.masterkun.kactor.cluster.membership;

import io.masterkun.kactor.cluster.LocalMemberState;

public interface MembershipListener extends MemberManager.Listener {
    default void onLocalStateChange(LocalMemberState currentState) {
    }

    ;
}
