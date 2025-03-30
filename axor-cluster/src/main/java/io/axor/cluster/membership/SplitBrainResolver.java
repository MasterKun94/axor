package io.axor.cluster.membership;

import io.axor.cluster.LocalMemberState;

public interface SplitBrainResolver extends MemberManager.Listener {

    LocalMemberState getLocalMemberState();

    String name();
}
