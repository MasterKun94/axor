package io.axor.raft;

import io.axor.api.ActorAddress;

public record Peer(int id, ActorAddress address) {
}
