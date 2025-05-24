package io.axor.raft.peer;

import io.axor.api.AbstractActor;
import io.axor.api.ActorAddress;
import io.axor.api.ActorContext;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.api.FailureStrategy;
import io.axor.raft.RaftConfig;
import io.axor.raft.RaftContext;
import io.axor.raft.RaftException;
import io.axor.raft.logging.RaftLoggingFactory;
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
    private Supplier<RaftContext> supplier;
    private RaftContext raftContext;

    public RaftPeerActor(ActorContext<PeerMessage> context, RaftConfig config,
                         List<ActorAddress> peers, int peerOffset, RaftLoggingFactory factory) {
        super(context);
        ActorAddress selfPeer = peers.get(peerOffset);
        supplier = () -> {
            try {
                return new RaftContext(context, config, peers, selfPeer, factory);
            } catch (RaftException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Override
    protected Behavior<PeerMessage> initialBehavior() {
        return Behaviors.receive(m -> Behaviors.unhandled(), s -> {
            if (s == START_SIGNAL) {
                Supplier<RaftContext> supplier = this.supplier;
                this.supplier = null;
                return new FollowerBehavior(raftContext = supplier.get());
            } else {
                return Behaviors.unhandled();
            }
        });
    }

    @Override
    public void preStop() {
        Behavior<PeerMessage> behavior = currentBehavior();
        if (behavior instanceof AbstractPeerBehavior peerBehavior) {
            peerBehavior.onStopped();
        }
        raftContext.close();
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
