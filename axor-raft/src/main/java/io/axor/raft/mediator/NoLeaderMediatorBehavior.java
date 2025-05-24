package io.axor.raft.mediator;

import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.raft.proto.PeerProto.MediatorMessage;
import io.axor.raft.proto.PeerProto.PeerMessage;

import java.util.ArrayList;
import java.util.List;

import static io.axor.runtime.stream.grpc.StreamUtils.protoToActorAddress;

public class NoLeaderMediatorBehavior extends AbstractMediatorBehavior {
    private List<MsgAndSender> delayMsg;
    private List<Runnable> delayRunnable;

    public NoLeaderMediatorBehavior(MediatorContext mediatorContext) {
        super(mediatorContext);
    }

    private void addDelayMsg(ActorContext<MediatorMessage> context, MediatorMessage msg) {
        if (delayMsg == null) {
            delayMsg = new ArrayList<>();
        }
        delayMsg.add(new MsgAndSender(msg, context.self()));
    }

    @Override
    protected Behavior<MediatorMessage> onTxnReq(ActorContext<MediatorMessage> context,
                                                 MediatorMessage msg) {
        addDelayMsg(context, msg);
        return Behaviors.same();
    }

    @Override
    protected Behavior<MediatorMessage> onTxnRes(ActorContext<MediatorMessage> context,
                                                 MediatorMessage msg) {
        if (msg.getTxnRes().getTerm() >= mediatorContext.getTerm()) {
            mediatorContext.ready(msg.getQueryRes().getTerm(), context.sender(PeerMessage.class));
            addDelayMsg(context, msg);
            return readyBehavior();
        }
        ReqCoordinator coordinator = mediatorContext.getReqCoordinators().get(msg.getSeqId());
        if (coordinator == null) {
            return Behaviors.unhandled();
        }
        coordinator.onSuccessRes(msg);
        return Behaviors.same();
    }

    @Override
    protected Behavior<MediatorMessage> onQueryReq(ActorContext<MediatorMessage> context,
                                                   MediatorMessage msg) {
        addDelayMsg(context, msg);
        return Behaviors.same();
    }

    @Override
    protected Behavior<MediatorMessage> onQueryRes(ActorContext<MediatorMessage> context,
                                                   MediatorMessage msg) {
        if (msg.getQueryRes().getTerm() >= mediatorContext.getTerm()) {
            mediatorContext.ready(msg.getQueryRes().getTerm(), context.sender(PeerMessage.class));
            addDelayMsg(context, msg);
            return readyBehavior();
        }
        ReqCoordinator coordinator = mediatorContext.getReqCoordinators().get(msg.getSeqId());
        if (coordinator == null) {
            return Behaviors.unhandled();
        }
        coordinator.onSuccessRes(msg);
        return Behaviors.same();
    }

    @Override
    protected Behavior<MediatorMessage> onFailureRes(ActorContext<MediatorMessage> context,
                                                     MediatorMessage msg) {
        ReqCoordinator coordinator = mediatorContext.getReqCoordinators().get(msg.getSeqId());
        if (coordinator == null) {
            return Behaviors.unhandled();
        }
        coordinator.onFailureRes(msg.getFailureRes());
        return Behaviors.same();
    }

    @Override
    protected Behavior<MediatorMessage> onRedirect(ActorContext<MediatorMessage> context,
                                                   MediatorMessage msg) {
        long currentTerm = mediatorContext.getTerm();
        MediatorMessage.Redirect redirect = msg.getRedirect();
        if (redirect.getTerm() >= currentTerm) {
            ActorRef<PeerMessage> leader;
            try {
                leader = context.system().get(protoToActorAddress(redirect.getPeer()),
                        PeerMessage.class);
            } catch (ActorNotFoundException | IllegalMsgTypeException e) {
                throw new RuntimeException(e);
            }
            mediatorContext.ready(redirect.getTerm(), leader);
            return readyBehavior();
        }
        return Behaviors.same();
    }

    @Override
    protected Behavior<MediatorMessage> onNoLeader(ActorContext<MediatorMessage> context,
                                                   MediatorMessage msg) {
        long currentTerm = mediatorContext.getTerm();
        long term = msg.getNoLeader().getTerm();
        if (term > currentTerm) {
            mediatorContext.noLeader(term);
            return noLeaderBehavior();
        }
        return Behaviors.same();
    }

    private Behavior<MediatorMessage> readyBehavior() {
        var b = Behaviors.consumeBuffer(() -> new ReadyMediatorBehavior(mediatorContext));
        if (delayRunnable != null) {
            delayRunnable.forEach(Runnable::run);
        }
        if (delayMsg != null) {
            delayMsg.forEach(buffer -> b.addMsg(buffer.msg, buffer.sender));
        }
        return b.toBehavior();
    }

    private Behavior<MediatorMessage> noLeaderBehavior() {
        var b = Behaviors.consumeBuffer(() -> new NoLeaderMediatorBehavior(mediatorContext));
        if (delayRunnable != null) {
            delayRunnable.forEach(Runnable::run);
        }
        if (delayMsg != null) {
            delayMsg.forEach(buffer -> b.addMsg(buffer.msg, buffer.sender));
        }
        return b.toBehavior();
    }

    private record MsgAndSender(MediatorMessage msg, ActorRef<?> sender) {
    }
}
