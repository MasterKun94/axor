package io.masterkun.kactor.cluster.membership;

public record MemberClock(long uid, VectorClock clock) {
    @Override
    public String toString() {
        return uid + "=" + clock;
    }
}
