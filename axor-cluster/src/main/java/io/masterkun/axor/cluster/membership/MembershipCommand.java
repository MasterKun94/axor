package io.masterkun.axor.cluster.membership;

public enum MembershipCommand implements MembershipMessage {
    JOIN, LEAVE, FORCE_LEAVE;
}
