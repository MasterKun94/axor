package io.masterkun.axor.api;

import io.masterkun.axor.api.impl.NoSenderActorRef;
import io.masterkun.axor.runtime.MsgType;

/**
 * Represents a reference to an actor, which can be used to send messages to the actor.
 * The type parameter T represents the type of message that this actor can receive.
 *
 * <p>This interface is sealed and permits only the implementation by {@code ActorRefRich}.
 *
 * @param <T> the type of message that the actor can receive
 */
public sealed interface ActorRef<T> permits ActorRefRich {
    /**
     * Returns a special {@code ActorRef} that represents the absence of a sender.
     * This is typically used when there is no specific sender for a message, such as in the case of system messages.
     *
     * @param <T> the type of message that the actor can receive
     * @return an {@code ActorRef<T>} representing the absence of a sender
     */
    static <T> ActorRef<T> noSender() {
        return NoSenderActorRef.get();
    }

    /**
     * Returns the message type that this actor is designed to handle.
     *
     * @return the {@code MsgType<? super T>} representing the type of messages this actor can process
     */
    MsgType<? super T> msgType();

    /**
     * Returns the address of the actor.
     *
     * @return the {@code ActorAddress} representing the unique address of the actor
     */
    ActorAddress address();

    /**
     * Sends a message to the actor referenced by this {@code ActorRef} without specifying a sender.
     * This method uses {@code ActorRef.noSender()} as the default sender, which represents the absence of a sender.
     *
     * @param value the message to send, which must be of type T
     */
    default void tell(T value) {
        tell(value, ActorRef.noSender());
    }

    /**
     * Sends a message to the actor referenced by this {@code ActorRef} with an explicit sender.
     *
     * @param value  the message to send, which must be of type T
     * @param sender the {@code ActorRef} representing the sender of the message
     */
    void tell(T value, ActorRef<?> sender);

    /**
     * Determines whether the actor referenced by this {@code ActorRef} is local to the current node.
     *
     * @return {@code true} if the actor is local, {@code false} otherwise
     */
    boolean isLocal();

    /**
     * Checks if this {@code ActorRef} represents the absence of a sender.
     *
     * @return {@code true} if this {@code ActorRef} is the special "no sender" reference, {@code false} otherwise
     */
    default boolean isNoSender() {
        return false;
    }

    /**
     * Attempts to cast this {@code ActorRef} to a new type, provided that the new type is supported by the current message type.
     *
     * @param <P>  the target message type of the actor reference
     * @param type the {@code MsgType<P>} representing the new message type to which the actor reference should be cast
     * @return an {@code ActorRef<P>} if the cast is successful
     * @throws IllegalArgumentException if the new message type is not supported by the current message type
     */
    @SuppressWarnings("unchecked")
    default <P> ActorRef<P> cast(MsgType<P> type) {
        if (msgType().isSupport(type)) {
            return (ActorRef<P>) this;
        } else {
            throw new IllegalArgumentException("Cannot cast " + type + " to " + msgType());
        }
    }
}
