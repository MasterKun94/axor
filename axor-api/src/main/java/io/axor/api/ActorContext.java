package io.axor.api;

import io.axor.api.impl.ActorSystemImpl;
import io.axor.commons.concurrent.EventStage;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.MsgType;
import io.axor.runtime.scheduler.Scheduler;

import java.util.List;

/**
 * Represents the context of an actor, providing access to essential components and operations
 * required for the actor's lifecycle and communication. This interface defines methods for
 * interacting with the actor system, managing child actors, handling messages, and observing system
 * events.
 * <p>
 * The {@code ActorContext} is responsible for maintaining the actor's reference, sender
 * information, and system-level services such as scheduling, dispatching, and event handling. It
 * also provides mechanisms for starting and stopping actors, managing watchers, and accessing
 * configuration settings.
 * <p>
 * Key functionalities include: - Retrieving references to the current actor, sender, and the actor
 * system. - Managing child actors by starting or stopping them. - Accessing system services like
 * the event dispatcher, scheduler, and dead letter queue. - Observing system events from other
 * actors through watch mechanisms. - Configuring actor behavior using settings such as
 * auto-acknowledgment. - Handling sessions associated with the actor.
 * <p>
 * This interface is designed to be implemented by concrete classes that provide the underlying
 * functionality for actor execution and management within an actor system.
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
     * Returns the {@code ActorRef} of the sender that sent the current message to this actor, with
     * a checked type.
     *
     * @param <P>         the type parameter representing the message type of the sender
     * @param checkedType the {@code MsgType<P>} to check and cast the sender's message type
     * @return the {@code ActorRef<P>} representing the sender of the current message, or a special
     * {@code ActorRef.noSender()} if there is no sender (e.g., in case of a system message)
     * @throws IllegalArgumentException if the checked type is not supported by the sender's message
     *                                  type
     */
    <P> ActorRef<P> sender(MsgType<P> checkedType);

    default <P> ActorRef<P> sender(Class<P> checkedType) {
        return sender(MsgType.of(checkedType));
    }

    /**
     * Returns the {@code ActorRef} for the current actor, which can be used to send messages to
     * itself.
     *
     * @return the {@code ActorRef<T>} representing the current actor
     */
    ActorRef<T> self();

    /**
     * Returns the {@code ActorSystem} that this actor is a part of. The actor system provides the
     * environment and services for all actors, including configuration, logging, and lifecycle
     * management.
     *
     * @return the {@code ActorSystem} associated with this actor
     */
    ActorSystem system();

    /**
     * Returns the {@code EventDispatcher} associated with this actor. The event executor is
     * responsible for executing tasks and scheduling operations in a thread-safe manner.
     *
     * @return the {@code EventDispatcher} used by this actor to execute tasks
     */
    EventDispatcher dispatcher();

    /**
     * Returns the {@code Scheduler} associated with this actor. The scheduler is responsible for
     * scheduling and executing tasks at specified intervals or after a certain delay.
     *
     * @return the {@code Scheduler} used by this actor for scheduling tasks
     */
    Scheduler scheduler();

    /**
     * Starts a new child actor with the specified creator and name.
     * <p>
     * The child actor and parent actor share the same thread, ensuring thread safety when modifying
     * the same data. The child actor's lifecycle is managed within the parent actor, and if the
     * parent actor stops, the child actor will also stop. During the stopping process, the parent
     * actor will first call the {@code preStop()} method, then stop its child actors, and finally
     * continue with the rest of the stopping process after all child actors have stopped.
     *
     * @param <P>     the type of messages that the child actor can handle
     * @param creator the {@code ActorCreator<P>} used to create the new actor instance
     * @param name    the name of the new child actor
     * @return an {@code ActorRef<P>} representing the newly created child actor
     */
    <P> ActorRef<P> startChild(ActorCreator<P> creator, String name);

    /**
     * Sends the given event to the dead letter event stream.
     * <p>
     * This method creates a {@code DeadLetter} message with the current actor's address, the
     * sender's reference, and the provided event. It then publishes this message to the dead letter
     * event stream of the actor system.
     *
     * @param event the event to be sent to the dead letter queue
     */
    default void deadLetter(T event) {
        var msg = new DeadLetter(self().address(), sender(), event);
        ((ActorSystemImpl) system()).deadLetters().publishToAll(msg, sender());
    }

    /**
     * Stops the current actor, triggering its termination and cleanup. This method sends a stop
     * signal to the actor system, which will then initiate the process of stopping the actor. The
     * actor's {@code preStop()} and {@code postStop()} methods will be called as part of the
     * termination process.
     */
    default void stop() {
        system().stop(self());
    }

    /**
     * Returns an {@code ActorRef} representing a special "no sender" reference. This method is
     * useful when there is no specific sender for a message, such as in the case of system
     * messages.
     *
     * @return the {@code ActorRef<T>} representing the "no sender" reference
     */
    default ActorRef<T> noSender() {
        return system().noSender();
    }

    /**
     * Registers the current actor to watch for specific system events from the given target actor.
     *
     * @param target      the {@code ActorRef} of the actor to be watched
     * @param watchEvents a list of {@code Class<? extends SystemEvent>} representing the types of
     *                    system events that should trigger a notification to the current actor
     */
    void watch(ActorRef<?> target, List<Class<? extends SystemEvent>> watchEvents);

    /**
     * Stops watching the specified target actor, ensuring that no further system event
     * notifications will be received from it.
     *
     * @param target the {@code ActorRef} of the actor to stop watching
     */
    void unwatch(ActorRef<?> target);

    /**
     * Retrieves the current settings for the actor, allowing access to configuration options such
     * as auto-acknowledgment.
     *
     * @return the {@code ActorSettings} object containing the current configuration of the actor
     */
    ActorSettings settings();

    ActorSessions<T> sessions();

    void signalWhenComplete(long tagId, EventStage<?> stage);
}
