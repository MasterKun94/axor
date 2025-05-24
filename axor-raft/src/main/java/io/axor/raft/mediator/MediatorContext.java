package io.axor.raft.mediator;

import io.axor.api.ActorRef;
import io.axor.commons.collection.LongObjectHashMap;
import io.axor.commons.collection.LongObjectMap;
import io.axor.raft.proto.PeerProto.PeerMessage;

import java.util.List;

public class MediatorContext {
    private final LongObjectMap<ReqCoordinator> reqCoordinators = new LongObjectHashMap<>();
    private long term;
    private ActorRef<PeerMessage> leader;
    private List<ActorRef<PeerMessage>> peers;

    public long getTerm() {
        return term;
    }

    public ActorRef<PeerMessage> getLeader() {
        return leader;
    }

    public List<ActorRef<PeerMessage>> getPeers() {
        return peers;
    }

    public void setPeers(List<ActorRef<PeerMessage>> peers) {
        this.peers = peers;
    }

    public LongObjectMap<ReqCoordinator> getReqCoordinators() {
        return reqCoordinators;
    }

    public void ready(long term, ActorRef<PeerMessage> leader) {
        assert (term == this.term && this.leader == null) || (term > this.term);
        this.term = term;
        this.leader = leader;
        reqCoordinators.values().forEach(ReqCoordinator::onReady);
    }

    public void redirect(long term, ActorRef<PeerMessage> leader) {
        assert term > this.term && this.leader != null;
        this.term = term;
        this.leader = leader;
        reqCoordinators.values().forEach(ReqCoordinator::onRedirect);
    }

    public void noLeader(long term) {
        assert term > this.term;
        this.term = term;
        ActorRef<PeerMessage> prev = this.leader;
        this.leader = null;
        if (prev != null) {
            reqCoordinators.values().forEach(ReqCoordinator::onNoLeader);
        }
    }
}
