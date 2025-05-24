package io.axor.raft.mediator;

import io.axor.api.ActorContext;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.raft.proto.PeerProto.MediatorMessage;
import io.axor.raft.proto.PeerProto.PeerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.axor.runtime.stream.grpc.StreamUtils.protoToActorAddress;

public class AbstractMediatorBehavior implements Behavior<MediatorMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractMediatorBehavior.class);

    protected final MediatorContext mediatorContext;

    public AbstractMediatorBehavior(MediatorContext mediatorContext) {
        this.mediatorContext = mediatorContext;
    }

    @Override
    public final Behavior<MediatorMessage> onReceive(ActorContext<MediatorMessage> context,
                                                     MediatorMessage message) {
        return switch (message.getMsgCase()) {
            case TXNREQ -> onTxnReq(context, message);
            case TXNRES -> onTxnRes(context, message);
            case QUERYREQ -> onQueryReq(context, message);
            case QUERYRES -> onQueryRes(context, message);
            case FAILURERES -> onFailureRes(context, message);
            case REDIRECT -> onRedirect(context, message);
            case NOLEADER -> onNoLeader(context, message);
            case MSG_NOT_SET -> throw new IllegalStateException("Message not set!");
        };
    }

    protected Behavior<MediatorMessage> onTxnReq(ActorContext<MediatorMessage> context,
                                                 MediatorMessage msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<MediatorMessage> onTxnRes(ActorContext<MediatorMessage> context,
                                                 MediatorMessage msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<MediatorMessage> onQueryReq(ActorContext<MediatorMessage> context,
                                                   MediatorMessage msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<MediatorMessage> onQueryRes(ActorContext<MediatorMessage> context,
                                                   MediatorMessage msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<MediatorMessage> onFailureRes(ActorContext<MediatorMessage> context,
                                                     MediatorMessage msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<MediatorMessage> onRedirect(ActorContext<MediatorMessage> context,
                                                   MediatorMessage msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<MediatorMessage> onNoLeader(ActorContext<MediatorMessage> context,
                                                   MediatorMessage msg) {
        return Behaviors.unhandled();
    }
}
