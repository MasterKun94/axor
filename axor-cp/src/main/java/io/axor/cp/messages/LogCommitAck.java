package io.axor.cp.messages;

public record LogCommitAck(long txnId,
                           CommitStatus status,
                           LogEntryId currentCommited) implements FollowerMessage, Ack {
    public boolean success() {
        return status == CommitStatus.SUCCESS;
    }
}
