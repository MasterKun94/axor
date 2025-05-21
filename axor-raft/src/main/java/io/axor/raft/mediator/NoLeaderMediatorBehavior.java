package io.axor.raft.mediator;

import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.MediatorMessage;

import java.util.ArrayList;
import java.util.List;

import static io.axor.runtime.stream.grpc.StreamUtils.protoToActorAddress;

public class NoLeaderMediatorBehavior extends AbstractMediatorBehavior {
    private List<MsgAndSender> delayMsg;
    private List<Runnable> delayRunnable;

    public NoLeaderMediatorBehavior(MediatorContext mediatorContext) {
        super(mediatorContext);
    }

    @Override
    protected Behavior<MediatorMessage> onTxnReq(ActorContext<MediatorMessage> context,
                                                 MediatorMessage.TxnReq msg) {
        if (delayMsg == null) {
            delayMsg = new ArrayList<>();
        }
        delayMsg.add(new MsgAndSender(
                MediatorMessage.newBuilder().setTxnReq(msg).build(),
                context.sender(MediatorMessage.class)));
        return Behaviors.same();
    }

    @Override
    protected Behavior<MediatorMessage> onTxnRes(ActorContext<MediatorMessage> context,
                                                 MediatorMessage.TxnRes msg) {
        return super.onTxnRes(context, msg);
    }

    @Override
    protected Behavior<MediatorMessage> onQueryReq(ActorContext<MediatorMessage> context,
                                                   MediatorMessage.QueryReq msg) {
        if (delayMsg == null) {
            delayMsg = new ArrayList<>();
        }
        delayMsg.add(new MsgAndSender(
                MediatorMessage.newBuilder().setQueryReq(msg).build(),
                context.sender(MediatorMessage.class)));
        return Behaviors.same();
    }

    @Override
    protected Behavior<MediatorMessage> onQueryRes(ActorContext<MediatorMessage> context,
                                                   MediatorMessage.QueryRes msg) {
        return super.onQueryRes(context, msg);
    }

    @Override
    protected Behavior<MediatorMessage> onFailureRes(ActorContext<MediatorMessage> context,
                                                     MediatorMessage.FailureRes msg) {
        return super.onFailureRes(context, msg);
    }

    @Override
    protected Behavior<MediatorMessage> onRedirect(ActorContext<MediatorMessage> context,
                                                   MediatorMessage.Redirect msg) {
        long currentTerm = mediatorContext.getTerm();
        if (msg.getTerm() >= currentTerm) {
            mediatorContext.setTerm(currentTerm);
            try {
                var ref = context.system().get(protoToActorAddress(msg.getPeer()),
                        PeerProto.PeerMessage.class);
                mediatorContext.setLeader(ref);
            } catch (ActorNotFoundException | IllegalMsgTypeException e) {
                throw new RuntimeException(e);
            }
            return readyBehavior();
        }
        return Behaviors.same();
    }

    @Override
    protected Behavior<MediatorMessage> onNoLeader(ActorContext<MediatorMessage> context,
                                                   MediatorMessage.NoLeader msg) {
        long currentTerm = mediatorContext.getTerm();
        if (msg.getTerm() > currentTerm) {
            mediatorContext.setTerm(currentTerm);
            mediatorContext.setLeader(null);
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

    private record MsgAndSender(MediatorMessage msg, ActorRef<MediatorMessage> sender) {
    }
}
