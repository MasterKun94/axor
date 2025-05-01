package io.axor.cp.messages;

public record LeaderState(LogEntryId currentCommited) implements LeaderMessage {
}
