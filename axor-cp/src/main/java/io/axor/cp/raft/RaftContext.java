package io.axor.cp.raft;

import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.cp.messages.LogEntryId;
import io.axor.cp.messages.Peer;
import io.axor.cp.messages.RaftMessage;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RaftContext {
    private final ActorSystem system;
    private final List<Peer> peers;
    private final Peer selfPeer;
    private final RaftConfig config;
    private long leaderLastHeartbeat;
    private LogEntryId commitedId;
    private Peer leaderPeer;

    public RaftContext(ActorSystem system, List<Peer> peers, Peer selfPeer, RaftConfig config) {
        this.system = system;
        this.peers = peers;
        this.selfPeer = selfPeer;
        this.config = config;
    }

    public RaftConfig config() {
        return config;
    }

    public List<Peer> peers() {
        return peers;
    }

    public long leaderLastHeartbeat() {
        return leaderLastHeartbeat;
    }

    public LogEntryId commitedId() {
        return commitedId;
    }

    public int majorityCount() {
        return peers.size() / 2 + 1;
    }

    @Nullable
    public Peer leaderPeer() {
        return leaderPeer;
    }

    public Peer selfPeer() {
        return selfPeer;
    }

    public boolean isLeader(Peer peer) {
        return peer.equals(leaderPeer);
    }

    public boolean isLeader(ActorRef<?> ref) {
        return ref.address().equals(leaderPeer.address());
    }

    public ActorRef<RaftMessage> peerRef(Peer peer) {
        try {
            return system.get(peer.address(), RaftMessage.class);
        } catch (ActorNotFoundException | IllegalMsgTypeException e) {
            throw new RuntimeException(e);
        }
    }

    public ActorRef<RaftMessage> replicatorRef(Peer peer) {
        try {
            ActorAddress address = peer.address();
            ActorAddress replicatorAddr = ActorAddress.create(address.system(), address.address(),
                    address.name() + "/replicator");
            return system.get(replicatorAddr, RaftMessage.class);
        } catch (ActorNotFoundException | IllegalMsgTypeException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateCommited(LogEntryId commitedId) {
        this.commitedId = commitedId;
    }

    public void updateLeaderLastHeartbeat() {
        this.leaderLastHeartbeat = System.currentTimeMillis();
    }

    public void updateLeader(ActorRef<?> peerRef) {
        for (Peer peer : peers) {
            if (peer.address().equals(peerRef.address())) {
                updateLeader(peer);
                return;
            }
        }
        throw new IllegalArgumentException("unrecognized peer ref: " + peerRef);
    }

    public void updateLeader(Peer peer) {
        assert peers.contains(peer);
        this.leaderPeer = peer;
    }
}
