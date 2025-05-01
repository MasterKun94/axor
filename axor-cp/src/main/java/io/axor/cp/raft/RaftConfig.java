package io.axor.cp.raft;

import io.axor.commons.config.MemorySize;

import java.time.Duration;

public record RaftConfig(
        Duration logAppendTimeout,
        MemorySize logAppendBytesLimit,
        Duration leaderHeartbeatInterval,
        Duration leaderHeartbeatTimeout,
        int followerIndexLagThreshold
) {
}
