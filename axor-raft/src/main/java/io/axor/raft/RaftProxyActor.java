package io.axor.raft;

import io.axor.api.Actor;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.commons.collection.LongObjectHashMap;
import io.axor.commons.collection.LongObjectMap;
import io.axor.commons.concurrent.EventStage;
import io.axor.commons.concurrent.Failure;
import io.axor.commons.concurrent.Success;
import io.axor.commons.concurrent.Try;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.ClientMessage;
import io.axor.raft.proto.PeerProto.PeerMessage;
import io.axor.raft.proto.PeerProto.ServerMessage;
import io.axor.runtime.MsgType;
import io.axor.runtime.stream.grpc.StreamUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RaftProxyActor extends Actor<ServerMessage> {
    private final LongObjectMap<ActorRef<ClientMessage>> clientSession = new LongObjectHashMap<>();

    private final ClientConfig config;
    private final Map<Peer, ActorRef<PeerMessage>> peers = new HashMap<>();
    private final List<Peer> initialPeers;
    private final long clientId;
    private ActorContext<ClientMessage> clientProxyContext;
    private ActorRef<ClientMessage> clientProxy;
    private ActorRef<PeerMessage> leader;
    private int inc = 0;
    private long seqId = 0;

    protected RaftProxyActor(ActorContext<ServerMessage> context, ClientConfig config,
                             List<Peer> initialPeers, long clientId) {
        super(context);
        this.config = config;
        this.initialPeers = new ArrayList<>(initialPeers);
        this.clientId = clientId;
    }

    @Override
    public void onStart() {
        String name = self().address().name() + "/client-proxy";
        clientProxy = context().startChild(ClientProxyActor::new, name);
    }

    private ActorRef<PeerMessage> getPeerRef(Peer peer) {
        return peers.computeIfAbsent(peer, k -> {
            try {
                return context().system().get(peer.address(), PeerMessage.class);
            } catch (ActorNotFoundException | IllegalMsgTypeException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private EventStage<ActorRef<PeerMessage>> seekForLeader() {
        if (leader == null) {
            Peer peer = initialPeers.get(inc++ % initialPeers.size());
            ActorRef<PeerMessage> ref = getPeerRef(peer);
            long seqId = this.seqId++;
            return clientProxyContext.sessions()
                    .expectReceiveFrom(ref)
                    .msgPredicate(m -> m.getSeqId() == seqId)
                    .listen(config.requestTimeout())
                    .transform(t -> {
                        if (t instanceof Success<ClientMessage>(var value)) {
                            switch (value.getResponseCase()) {
                                case REDIRECT -> {
                                    PeerProto.Peer proto = value.getRedirect().getPeer();
                                    Peer p = new Peer(proto.getId(),
                                            StreamUtils.protoToActorAddress(proto.getAddress()));
                                    leader = getPeerRef(p);
                                    return Try.success(leader);
                                }
                                case FAILURE -> {
                                    var e = new RaftException(value.getFailure().getMessage());
                                    return Try.failure(e);
                                }
                            }
                            var e = new IllegalArgumentException("Unexpected response type: " +
                                                                 value.getResponseCase());
                            return Try.failure(e);
                        } else if (t instanceof Failure<ClientMessage>(var value)) {
                            return Try.failure(value);
                        } else {
                            throw new IllegalArgumentException("Unexpected response type: " +
                                                               t.getClass().getSimpleName());
                        }
                    });
        }
        return EventStage.succeed(leader, context().dispatcher());
    }

    @Override
    public void onReceive(ServerMessage msg) {

    }

    @Override
    public MsgType<ServerMessage> msgType() {
        return MsgType.of(ServerMessage.class);
    }

    private class ClientProxyActor extends Actor<ClientMessage> {

        protected ClientProxyActor(ActorContext<ClientMessage> context) {
            super(context);
            clientProxyContext = context;
        }

        @Override
        public void onReceive(ClientMessage clientMessage) {

        }

        @Override
        public MsgType<ClientMessage> msgType() {
            return MsgType.of(ClientMessage.class);
        }
    }
}
