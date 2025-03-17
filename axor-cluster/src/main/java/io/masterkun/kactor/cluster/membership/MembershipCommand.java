package io.masterkun.kactor.cluster.membership;

public enum MembershipCommand implements MembershipMessage {
    JOIN, LEAVE, FORCE_LEAVE;
}
