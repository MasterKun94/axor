package io.axor.raft;

import io.axor.api.ActorRef;
import io.axor.raft.messages.PeerMessage;

public record PeerInstance(Peer peer,
                           boolean isSelf,
                           ActorRef<PeerMessage> peerRef) {
}
