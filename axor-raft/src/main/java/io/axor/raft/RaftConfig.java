package io.axor.raft;

import io.axor.commons.config.ConfigField;
import io.axor.commons.config.MemorySize;

import java.time.Duration;

public record RaftConfig(
        @ConfigField(value = "logAppendTimeout", fallback = "1s")
        Duration logAppendTimeout,
        @ConfigField(value = "logAppendEntryLimit", fallback = "32")
        int logAppendEntryLimit,
        @ConfigField(value = "logAppendBytesLimit", fallback = "64k")
        MemorySize logAppendBytesLimit,
        @ConfigField(value = "leaderHeartbeatInterval", fallback = "1s")
        Duration leaderHeartbeatInterval,
        @ConfigField(value = "leaderHeartbeatTimeout", fallback = "5s")
        Duration leaderHeartbeatTimeout,
        @ConfigField(value = "followerIndexLagThreshold", fallback = "32")
        int followerIndexLagThreshold,
        @ConfigField(value = "logFetchRetryInterval", fallback = "3s")
        Duration logFetchRetryInterval,
        @ConfigField(value = "logFetchTimeout", fallback = "5s")
        Duration logFetchTimeout,
        @ConfigField(value = "logFetchEntryLimit", fallback = "32")
        int logFetchEntryLimit,
        @ConfigField(value = "logFetchBytesLimit", fallback = "64k")
        MemorySize logFetchBytesLimit,
        @ConfigField(value = "candidateTimeoutBase", fallback = "1s")
        Duration candidateTimeoutBase,
        @ConfigField(value = "candidateTimeoutRandomRatio", fallback = "0.5")
        double candidateTimeoutRandomRatio,
        @ConfigField(value = "clientTxnCacheCheckTimeout", fallback = "1d")
        Duration clientTxnCacheCheckTimeout,
        @ConfigField(value = "clientTxnCacheCheckInterval", fallback = "1h")
        Duration clientTxnCacheCheckInterval,
        @ConfigField(value = "snapshotEntryInterval", fallback = "10000")
        int snapshotEntryInterval,
        @ConfigField(value = "snapshotBytesInterval", fallback = "128m")
        MemorySize snapshotBytesInterval,
        @ConfigField(value = "snapshotTimeout", fallback = "60s")
        Duration snapshotTimeout
) {
}
