package io.masterkun.axor.cluster.membership;

import io.masterkun.axor.cluster.LocalMemberState;

public interface SplitBrainResolver extends MemberManager.Listener {

    LocalMemberState getLocalMemberState();

    String name();
}
