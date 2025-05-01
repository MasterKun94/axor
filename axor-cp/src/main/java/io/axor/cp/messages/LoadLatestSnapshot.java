package io.axor.cp.messages;

public record LoadLatestSnapshot(LogEntryId currentCommited) implements LeaderMessage {
}
