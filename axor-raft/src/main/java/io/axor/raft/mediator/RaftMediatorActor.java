package io.axor.raft.mediator;

import io.axor.api.AbstractActor;
import io.axor.api.ActorAddress;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.commons.collection.LongObjectHashMap;
import io.axor.commons.collection.LongObjectMap;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.raft.ClientConfig;
import io.axor.raft.proto.PeerProto.MediatorMessage;
import io.axor.raft.proto.PeerProto.PeerMessage;
import io.axor.runtime.MsgType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RaftMediatorActor extends AbstractActor<MediatorMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(RaftMediatorActor.class);

    private final LongObjectMap<ReqAndActor> clientRequests = new LongObjectHashMap<>();
    private final ClientConfig config;
    private final Map<ActorAddress, ActorRef<PeerMessage>> peers = new HashMap<>();
    private final List<ActorAddress> initialPeers;
    private final long clientId;
    private ActorRef<PeerMessage> leader;
    private long term = 0;
    private int inc = 0;
    private long seqId = 0;

    protected RaftMediatorActor(ActorContext<MediatorMessage> context, ClientConfig config,
                                List<ActorAddress> initialPeers, long clientId) {
        super(context);
        this.config = config;
        this.initialPeers = new ArrayList<>(initialPeers);
        this.clientId = clientId;
    }

    private ActorRef<PeerMessage> getPeerRef(ActorAddress peer) {
        return peers.computeIfAbsent(peer, k -> {
            try {
                return context().system().get(peer, PeerMessage.class);
            } catch (ActorNotFoundException | IllegalMsgTypeException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    protected Behavior<MediatorMessage> initialBehavior() {
        return null;
    }

    protected Behavior<MediatorMessage> noLeaderBehavior() {
        return Behaviors.receiveMessage(msg -> {


            return Behaviors.same();
        });
    }

    @Override
    public MsgType<MediatorMessage> msgType() {
        return MsgType.of(MediatorMessage.class);
    }

    private record ReqAndActor(PeerMessage msg, ActorRef<MediatorMessage> client,
                               long clientSeqId) {
    }

}
