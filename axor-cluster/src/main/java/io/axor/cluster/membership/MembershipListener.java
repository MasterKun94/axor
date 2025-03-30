package io.axor.cluster.membership;

import io.axor.cluster.LocalMemberState;
import org.jetbrains.annotations.ApiStatus.Internal;

@Internal
public interface MembershipListener extends MemberManager.Listener {
    default void onLocalStateChange(LocalMemberState currentState) {
    }

    default void onLocalMemberStopped() {
    }
}
