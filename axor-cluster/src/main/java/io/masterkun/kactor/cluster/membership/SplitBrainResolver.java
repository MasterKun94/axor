package io.masterkun.kactor.cluster.membership;

import io.masterkun.kactor.cluster.LocalMemberState;

public interface SplitBrainResolver extends MemberManager.Listener {

    LocalMemberState getLocalMemberState();

    String name();
}
