package io.axor.cp.messages;

public record LogCommit(long txnId,
                        LogEntryId commitId,
                        LogEntryId currentCommited) implements LeaderMessage {
}
