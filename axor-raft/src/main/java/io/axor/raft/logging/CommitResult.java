package io.axor.raft.logging;

import io.axor.raft.CommitStatus;
import io.axor.raft.LogId;

public record CommitResult(CommitStatus status, LogId commited) {
}
