package io.axor.cp.messages;

public record RequestVote(LogEntryId currentCommited) implements CandidateMessage {
}
