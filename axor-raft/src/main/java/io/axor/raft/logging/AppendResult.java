package io.axor.raft.logging;

import io.axor.raft.AppendStatus;
import io.axor.raft.LogId;

import java.util.List;

public record AppendResult(AppendStatus status, LogId commited, List<LogId> uncommited) {
}
