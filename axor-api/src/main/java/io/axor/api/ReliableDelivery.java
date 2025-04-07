package io.axor.api;

import io.axor.api.impl.ActorUnsafe;
import io.axor.runtime.EventContext;
import io.axor.runtime.EventContextKeyMarshaller;
import io.axor.runtime.Signal;

public class ReliableDelivery {
    public static final EventContext.Key<Long> MSG_ID = new EventContext.Key<>(1,
            "reliable_delivery_msg_id",
            "Message ID of Reliable Delivery",
            EventContextKeyMarshaller.LONG);
    private static final AckHook NOOP_ACK = new AckHook() {
        @Override
        public void ack() {
        }

        @Override
        public void failure(Throwable cause) {
        }
    };

    private final ActorContext<?> context;

    private ReliableDelivery(ActorContext<?> context) {
        this.context = context;
    }

    public static ReliableDelivery get(ActorContext<?> context) {
        return new ReliableDelivery(context);
    }

    public boolean isAutoAck() {
        return context.settings().isAutoAck();
    }

    public void setAutoAck(boolean enabled) {
        context.settings().setAutoAck(enabled);
    }

    public void ack() {
        assert context.dispatcher().inExecutor();
        if (context.settings().isAutoAck()) {
            return;
        }
        ActorUnsafe.msgAck(context);
    }

    public void ackFailed(Throwable cause) {
        assert context.dispatcher().inExecutor();
        if (context.settings().isAutoAck()) {
            return;
        }
        ActorUnsafe.msgAckFailed(context, cause);
    }

    public AckHook createAckHook() {
        if (isAutoAck()) {
            return NOOP_ACK;
        }
        Long l = context.dispatcher().getContext().get(MSG_ID);
        if (l == null) {
            return NOOP_ACK;
        }
        ActorRef<?> sender = context.sender();
        return new AckHook() {
            @Override
            public void ack() {
                ActorUnsafe.signal(sender, new MsgAckSuccess(l), context.self());
            }

            @Override
            public void failure(Throwable cause) {
                ActorUnsafe.signal(sender, new MsgAckFailed(l, cause), context.self());
            }
        };
    }

    public sealed interface MsgAckStatus extends Signal {

    }

    public interface AckHook {
        void ack();

        void failure(Throwable cause);
    }

    public record MsgAckSuccess(long msgId) implements MsgAckStatus {
    }

    public record MsgAckFailed(long msgId, Throwable cause) implements MsgAckStatus {
    }
}
