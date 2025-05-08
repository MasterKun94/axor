package io.axor.raft;

import io.axor.api.AbstractActor;
import io.axor.api.ActorContext;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.raft.behaviors.FollowerBehavior;
import io.axor.raft.logging.AsyncRaftLogging;
import io.axor.raft.messages.PeerMessage;
import io.axor.runtime.MsgType;
import io.axor.runtime.Signal;

import java.util.List;
import java.util.function.Supplier;

public class PeerActor extends AbstractActor<PeerMessage> {
    public static final Signal START_SIGNAL = new Signal() {
        @Override
        public String toString() {
            return "PEER_START_SIGNAL";
        }
    };
    private Supplier<RaftContext> raftContextSupplier;

    protected PeerActor(ActorContext<PeerMessage> context, RaftConfig config, List<Peer> peers,
                        int peerOffset, AsyncRaftLogging raftLogging) {
        super(context);
        raftContextSupplier = () -> new RaftContext(context, config, peers, peers.get(peerOffset)
                , raftLogging);
    }

    @Override
    protected Behavior<PeerMessage> initialBehavior() {
        return Behaviors.receive(m -> Behaviors.unhandled(), s -> {
            if (s == START_SIGNAL) {
                Supplier<RaftContext> supplier = raftContextSupplier;
                raftContextSupplier = null;
                return new FollowerBehavior(supplier.get());
            } else {
                return Behaviors.unhandled();
            }
        });
    }

    @Override
    public MsgType<PeerMessage> msgType() {
        return MsgType.of(PeerMessage.class);
    }
}
