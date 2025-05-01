package io.axor.cp.messages;

public sealed interface FollowerMessage extends NodeMessage permits LogAppendAck, LogCommitAck {
}
