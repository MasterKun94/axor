package io.axor.raft;

import io.axor.api.AbstractActor;
import io.axor.api.ActorContext;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.api.FailureStrategy;
import io.axor.raft.behaviors.FollowerBehavior;
import io.axor.raft.logging.RaftLogging;
import io.axor.raft.proto.PeerProto.PeerMessage;
import io.axor.runtime.MsgType;
import io.axor.runtime.Signal;

import java.util.List;
import java.util.function.Supplier;

public class RaftPeerActor extends AbstractActor<PeerMessage> {
    public static final Signal START_SIGNAL = new Signal() {
        @Override
        public String toString() {
            return "PEER_START_SIGNAL";
        }
    };
    private Supplier<RaftContext> raftContextSupplier;

    protected RaftPeerActor(ActorContext<PeerMessage> context, RaftConfig config, List<Peer> peers,
                            int peerOffset, RaftLogging raftLogging) {
        super(context);
        raftContextSupplier = () -> {
            Peer selfPeer = peers.get(peerOffset);
            return new RaftContext(context, config, peers, selfPeer, raftLogging);
        };
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
    public FailureStrategy failureStrategy(Throwable throwable) {
        return FailureStrategy.SYSTEM_ERROR;
    }

    @Override
    public MsgType<PeerMessage> msgType() {
        return MsgType.of(PeerMessage.class);
    }
}
