package io.masterkun.axor.api;

import io.masterkun.axor.api.impl.ActorSystemImpl;
import io.masterkun.axor.runtime.EventDispatcher;

/**
 * Provides the context for an actor, encapsulating the environment in which the actor operates.
 * This includes references to the actor system, the event executor, and methods to interact with other actors.
 *
 * @param <T> the type of messages this actor can handle
 */
public interface ActorContext<T> {
    /**
     * Returns the {@code ActorRef} of the sender that sent the current message to this actor.
     *
     * @return the {@code ActorRef<?>} representing the sender of the current message, or a special
     * {@code ActorRef.noSender()} if there is no sender (e.g., in case of a system message)
     */
    ActorRef<?> sender();

    /**
     * Returns the {@code ActorRef} for the current actor, which can be used to send messages to itself.
     *
     * @return the {@code ActorRef<T>} representing the current actor
     */
    ActorRef<T> self();

    /**
     * Returns the {@code ActorSystem} that this actor is a part of. The actor system provides the
     * environment and services for all actors, including configuration, logging, and lifecycle management.
     *
     * @return the {@code ActorSystem} associated with this actor
     */
    ActorSystem system();

    /**
     * Returns the {@code EventExecutor} associated with this actor. The event executor is responsible for
     * executing tasks and scheduling operations in a thread-safe manner.
     *
     * @return the {@code EventExecutor} used by this actor to execute tasks
     */
    EventDispatcher executor();

    default void deadLetter(T event) {
        var msg = new DeadLetter(self().address(), sender(), event);
        ((ActorSystemImpl) system())
                .deadLetters()
                .publishToAll(msg, sender());
    }

    /**
     * Stops the current actor, triggering its termination and cleanup.
     * This method sends a stop signal to the actor system, which will then initiate the process of stopping the actor.
     * The actor's {@code preStop()} and {@code postStop()} methods will be called as part of the termination process.
     */
    default void stop() {
        system().stop(self());
    }

    /**
     * Returns an {@code ActorRef} representing a special "no sender" reference.
     * This method is useful when there is no specific sender for a message, such as in the case of system messages.
     *
     * @return the {@code ActorRef<T>} representing the "no sender" reference
     */
    default ActorRef<T> noSender() {
        return system().noSender();
    }
}
