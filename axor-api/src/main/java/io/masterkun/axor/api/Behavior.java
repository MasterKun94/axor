package io.masterkun.axor.api;

/**
 * Defines the behavior of an actor, which is the core component for processing messages in an actor
 * system. The behavior defines how an actor should handle incoming messages and can be changed over
 * time to reflect different states or modes of operation.
 *
 * @param <T> the type of messages this actor can handle
 */
public interface Behavior<T> {
    /**
     * Defines the behavior of an actor when it receives a message.
     *
     * <p>This method is called by the actor system whenever a message is delivered to the actor.
     * The implementation of this method should define how the actor processes the message and can
     * return a new behavior to change the actor's state or continue with the current behavior.
     *
     * @param context the {@code ActorContext} providing the environment in which the actor
     *                operates
     * @param message the message received by the actor, of type T
     * @return the new or updated behavior of the actor after processing the message
     */
    Behavior<T> onReceive(ActorContext<T> context, T message);
}
