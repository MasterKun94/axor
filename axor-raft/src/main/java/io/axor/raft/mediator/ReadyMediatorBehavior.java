package io.axor.raft.mediator;

import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.MediatorMessage;

import static io.axor.runtime.stream.grpc.StreamUtils.protoToActorAddress;

public class ReadyMediatorBehavior extends AbstractMediatorBehavior {
    public ReadyMediatorBehavior(MediatorContext mediatorContext) {
        super(mediatorContext);
    }

    @Override
    protected Behavior<MediatorMessage> onTxnReq(ActorContext<MediatorMessage> context,
                                                 MediatorMessage msg) {
        return super.onTxnReq(context, msg);
    }

    @Override
    protected Behavior<MediatorMessage> onTxnRes(ActorContext<MediatorMessage> context,
                                                 MediatorMessage msg) {
        return super.onTxnRes(context, msg);
    }

    @Override
    protected Behavior<MediatorMessage> onQueryReq(ActorContext<MediatorMessage> context,
                                                   MediatorMessage msg) {
        return super.onQueryReq(context, msg);
    }

    @Override
    protected Behavior<MediatorMessage> onQueryRes(ActorContext<MediatorMessage> context,
                                                   MediatorMessage msg) {
        return super.onQueryRes(context, msg);
    }

    @Override
    protected Behavior<MediatorMessage> onFailureRes(ActorContext<MediatorMessage> context,
                                                     MediatorMessage msg) {
        return super.onFailureRes(context, msg);
    }

    @Override
    protected Behavior<MediatorMessage> onRedirect(ActorContext<MediatorMessage> context,
                                                   MediatorMessage msg) {
        long currentTerm = mediatorContext.getTerm();
        MediatorMessage.Redirect redirect = msg.getRedirect();
        if (redirect.getTerm() > currentTerm) {
            ActorRef<PeerProto.PeerMessage> leader;
            try {
                leader = context.system().get(protoToActorAddress(redirect.getPeer()),
                        PeerProto.PeerMessage.class);
            } catch (ActorNotFoundException | IllegalMsgTypeException e) {
                throw new RuntimeException(e);
            }
            mediatorContext.redirect(redirect.getTerm(), leader);
            return new ReadyMediatorBehavior(mediatorContext);
        }
        return Behaviors.unhandled();
    }

    @Override
    protected Behavior<MediatorMessage> onNoLeader(ActorContext<MediatorMessage> context,
                                                   MediatorMessage msg) {
        long currentTerm = mediatorContext.getTerm();
        long term = msg.getNoLeader().getTerm();
        if (term > currentTerm) {
            mediatorContext.noLeader(term);
            return new NoLeaderMediatorBehavior(mediatorContext);
        }
        return Behaviors.unhandled();
    }
}
