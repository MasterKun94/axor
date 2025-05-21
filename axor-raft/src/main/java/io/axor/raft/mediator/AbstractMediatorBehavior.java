package io.axor.raft.mediator;

import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
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
            case TXNREQ -> onTxnReq(context, message.getTxnReq());
            case TXNRES -> onTxnRes(context, message.getTxnRes());
            case QUERYREQ -> onQueryReq(context, message.getQueryReq());
            case QUERYRES -> onQueryRes(context, message.getQueryRes());
            case FAILURERES -> onFailureRes(context, message.getFailureRes());
            case REDIRECT -> onRedirect(context, message.getRedirect());
            case NOLEADER -> onNoLeader(context, message.getNoLeader());
            case MSG_NOT_SET -> throw new IllegalStateException("Message not set!");
        };
    }

    protected Behavior<MediatorMessage> onTxnReq(ActorContext<MediatorMessage> context,
                                                 MediatorMessage.TxnReq msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<MediatorMessage> onTxnRes(ActorContext<MediatorMessage> context,
                                                 MediatorMessage.TxnRes msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<MediatorMessage> onQueryReq(ActorContext<MediatorMessage> context,
                                                   MediatorMessage.QueryReq msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<MediatorMessage> onQueryRes(ActorContext<MediatorMessage> context,
                                                   MediatorMessage.QueryRes msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<MediatorMessage> onFailureRes(ActorContext<MediatorMessage> context,
                                                     MediatorMessage.FailureRes msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<MediatorMessage> onRedirect(ActorContext<MediatorMessage> context,
                                                   MediatorMessage.Redirect msg) {
        long currentTerm = mediatorContext.getTerm();
        if (msg.getTerm() > currentTerm) {
            mediatorContext.setTerm(currentTerm);
            try {
                var ref = context.system().get(protoToActorAddress(msg.getPeer()), PeerMessage.class);
                mediatorContext.setLeader(ref);
            } catch (ActorNotFoundException | IllegalMsgTypeException e) {
                throw new RuntimeException(e);
            }
            return new ReadyMediatorBehavior(mediatorContext);
        } else if (msg.getTerm() == currentTerm && mediatorContext.getLeader() == null) {
            try {
                var ref = context.system().get(protoToActorAddress(msg.getPeer()), PeerMessage.class);
                mediatorContext.setLeader(ref);
            } catch (ActorNotFoundException | IllegalMsgTypeException e) {
                throw new RuntimeException(e);
            }
            return new ReadyMediatorBehavior(mediatorContext);
        }
        return Behaviors.same();
    }

    protected Behavior<MediatorMessage> onNoLeader(ActorContext<MediatorMessage> context,
                                                   MediatorMessage.NoLeader msg) {
        long currentTerm = mediatorContext.getTerm();
        if (msg.getTerm() > currentTerm) {
            mediatorContext.setTerm(currentTerm);
            mediatorContext.setLeader(null);
            return new NoLeaderMediatorBehavior(mediatorContext);
        }
        return Behaviors.same();
    }
}
