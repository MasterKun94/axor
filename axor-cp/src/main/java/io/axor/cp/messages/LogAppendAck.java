package io.axor.cp.messages;

public record LogAppendAck(long txnId,
                           AppendStatus status,
                           LogEntryId currentCommited) implements FollowerMessage, Ack {
    public boolean success() {
        return status == AppendStatus.SUCCESS;
    }
}
