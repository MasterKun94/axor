package io.axor.cp.messages;

public sealed interface CandidateMessage extends NodeMessage permits RequestVote {
}
