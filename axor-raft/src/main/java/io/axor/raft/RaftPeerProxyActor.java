package io.axor.raft;

import io.axor.api.Actor;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.raft.proto.PeerProto.PeerMessage;
import io.axor.runtime.MsgType;

import java.util.ArrayList;
import java.util.List;

public class RaftPeerProxyActor extends Actor<PeerMessage> {
    private final List<PeerInstance> peers;

    protected RaftPeerProxyActor(ActorContext<PeerMessage> context, List<Peer> peers) throws Exception {
        super(context);
        this.peers = new ArrayList<>(peers.size());
        for (Peer peer : peers) {
            ActorRef<PeerMessage> ref = context.system().get(peer.address(), PeerMessage.class);
            this.peers.add(new PeerInstance(peer, false, ref));
        }
    }

    @Override
    public void onReceive(PeerMessage msg) {

    }

    @Override
    public MsgType<PeerMessage> msgType() {
        return MsgType.of(PeerMessage.class);
    }
}
