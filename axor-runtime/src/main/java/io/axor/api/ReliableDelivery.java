package io.axor.api;

import io.axor.api.impl.ActorUnsafe;
import io.axor.runtime.EventContext;
import io.axor.runtime.EventContextKeyMarshaller;
import io.axor.runtime.Signal;

/**
 * Provides a mechanism for ensuring reliable message delivery in an actor system. This class allows
 * for the acknowledgment of messages and handling of failures, with support for auto-acknowledgment
 * and custom acknowledgment hooks.
 */
public class ReliableDelivery {
    public static final EventContext.Key<Long> MSG_ID = new EventContext.Key<>(1,
            "reliable_delivery_msg_id", "Message ID of Reliable Delivery",
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

    /**
     * Wraps the current event context with a specified message ID.
     *
     * @param id the message ID to be added to the event context
     * @return the new event context with the message ID added
     */
    public static EventContext wrap(long id) {
        return wrap(EventContext.current(), id);
    }

    /**
     * Wraps the given event context with a message ID.
     *
     * @param eventContext the event context to be wrapped
     * @param id           the message ID to be added to the event context
     * @return the new event context with the message ID added
     */
    public static EventContext wrap(EventContext eventContext, long id) {
        return eventContext.with(MSG_ID, id, 1);
    }

    /**
     * Checks if the auto-acknowledgment feature is enabled for the current actor.
     *
     * @return {@code true} if auto-acknowledgment is enabled, otherwise {@code false}
     */
    public boolean isAutoAck() {
        return context.settings().isAutoAck();
    }

    /**
     * Enables or disables the auto-acknowledgment feature for the current actor.
     * <p>
     * When auto-acknowledgment is enabled, messages are automatically acknowledged without the need
     * for explicit acknowledgment. If disabled, manual acknowledgment is required using the
     * provided acknowledgment methods.
     *
     * @param enabled {@code true} to enable auto-acknowledgment, {@code false} to disable it
     */
    public void setAutoAck(boolean enabled) {
        context.settings().setAutoAck(enabled);
    }

    private void checkInExecutor() {
        if (!context.dispatcher().inExecutor()) {
            throw new RuntimeException("not in executor");
        }
    }

    /**
     * Acknowledges the current message being processed, if auto-acknowledgment is not enabled.
     * <p>
     * This method first ensures that it is being called from the correct executor thread. If
     * auto-acknowledgment is enabled, the method returns immediately without performing any action.
     * Otherwise, it proceeds to acknowledge the message by sending a success signal to the sender
     * of the message.
     * <p>
     * The acknowledgment process involves removing the message ID from the event context and
     * signaling the sender with a success acknowledgment.
     */
    public void ack() {
        checkInExecutor();
        if (context.settings().isAutoAck()) {
            return;
        }
        ActorUnsafe.msgAck(context);
    }

    /**
     * Acknowledges the failure of the current message being processed, if auto-acknowledgment is
     * not enabled.
     * <p>
     * This method first ensures that it is being called from the correct executor thread. If
     * auto-acknowledgment is enabled, the method returns immediately without performing any action.
     * Otherwise, it proceeds to acknowledge the failure by sending a failure signal to the sender
     * of the message.
     *
     * @param cause the {@code Throwable} representing the cause of the failure
     */
    public void ackFailed(Throwable cause) {
        checkInExecutor();
        if (context.settings().isAutoAck()) {
            return;
        }
        ActorUnsafe.msgAckFailed(context, cause);
    }

    /**
     * Creates and returns an {@code AckHook} instance based on the current auto-acknowledgment
     * setting.
     * <p>
     * If auto-acknowledgment is enabled, a no-op {@code AckHook} is returned. Otherwise, it creates
     * an {@code AckHook} that can be used to manually acknowledge or report the failure of the
     * message being processed. The acknowledgment or failure is signaled to the sender of the
     * message.
     * <p>
     * The {@code AckHook} is designed to handle asynchronous message processing scenarios. If the
     * acknowledgment needs to wait for an asynchronous task to complete before being submitted, the
     * {@code AckHook} can be used to perform the acknowledgment.
     *
     * @return an {@code AckHook} instance for manual acknowledgment, or a no-op {@code AckHook} if
     * auto-acknowledgment is enabled
     */
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
