package io.axor.cp.messages;

public sealed interface LeaderMessage extends NodeMessage
        permits LeaderState, LoadLatestSnapshot, LogAppend, LogCommit {
}
