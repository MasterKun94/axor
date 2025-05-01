package io.axor.cp.messages;

public sealed interface NodeMessage extends RaftMessage
        permits LeaderMessage, FollowerMessage, CandidateMessage {
    LogEntryId currentCommited();
}
