package io.axor.api;

import io.axor.runtime.MsgType;
import io.axor.runtime.Signal;

/**
 * Abstract base class for actors, which are the fundamental unit of computation in an actor system.
 * Actors process messages sent to them and can manage their own state and lifecycle.
 *
 * <p>This class provides methods for handling the lifecycle of an actor, including start, receive,
 * and stop phases. It also allows for specifying a failure strategy when an exception is thrown
 * during message processing.
 *
 * @param <T> the type of messages that this actor can handle
 */
public abstract class Actor<T> {
    private final ActorContext<T> context;

    protected Actor(ActorContext<T> context) {
        this.context = context;
    }

    /**
     * Returns the context of this actor, which provides access to the actor's environment and
     * allows interaction with the actor system.
     *
     * @return the {@code ActorContext} associated with this actor
     */
    public ActorContext<T> context() {
        return context;
    }

    /**
     * Returns the {@code ActorRef} for the current actor, which can be used to send messages to
     * itself.
     *
     * @return the {@code ActorRef<T>} representing the current actor
     */
    public ActorRef<T> self() {
        return context.self();
    }

    /**
     * Returns the {@code ActorRef} of the sender that sent the current message to this actor.
     *
     * @return the {@code ActorRef<?>} representing the sender of the current message, or a special
     * {@code ActorRef.noSender()} if there is no sender (e.g., in case of a system message)
     */
    public ActorRef<?> sender() {
        return context.sender();
    }

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
    public <P> ActorRef<P> sender(MsgType<P> checkedType) {
        return context.sender(checkedType);
    }

    /**
     * Returns the {@code ActorRef} of the sender that sent the current message to this actor, with
     * a checked type.
     *
     * @param <P>         the type parameter representing the message type of the sender
     * @param checkedType the class representing the message type to check and cast the sender's
     *                    message type
     * @return the {@code ActorRef<P>} representing the sender of the current message, or a special
     * {@code ActorRef.noSender()} if there is no sender (e.g., in case of a system message)
     * @throws IllegalArgumentException if the checked type is not supported by the sender's message
     *                                  type
     */
    public <P> ActorRef<P> sender(Class<P> checkedType) {
        return sender(MsgType.of(checkedType));
    }

    /**
     * Called when the actor is started. This method can be overridden to perform initialization
     * tasks that need to be executed when the actor is first created and before it begins
     * processing messages.
     *
     * <p>Typical use cases for this method include setting up initial state, subscribing to event
     * streams, or establishing connections with other actors or external systems.
     */
    public void onStart() {
    }

    /**
     * Called when the actor is being restarted. This method can be overridden to perform any
     * necessary actions that need to be executed when the actor is being restarted, such as
     * resetting state or reinitializing resources.
     *
     * <p>Restarting an actor typically occurs in response to a failure, and this method provides a
     * hook to handle such scenarios. The default implementation does nothing.
     */
    public void onRestart() {
    }

    /**
     * Called when the actor receives a message. This method must be implemented by concrete actor
     * classes to define how the actor should process incoming messages.
     *
     * @param t the message received by the actor, of type T
     */
    public abstract void onReceive(T t);

    /**
     * Handles a signal received by the actor.
     *
     * <p>This method is called when the actor receives a {@code Signal}. It can be overridden to
     * provide custom handling for signals, such as performing specific actions or updating the
     * actor's state.
     *
     * @param signal the signal received by the actor
     */
    public void onSignal(Signal signal) {
    }

    /**
     * Called before the actor is stopped. This method can be overridden to perform any necessary
     * cleanup or finalization tasks that need to be executed before the actor is terminated.
     *
     * <p>This is a good place to release resources, unsubscribe from event streams, or send
     * final messages to other actors. The default implementation does nothing.
     */
    public void preStop() {
    }

    /**
     * Called after the actor is stopped. This method can be overridden to perform any necessary
     * finalization tasks that need to be executed after the actor has been terminated.
     *
     * <p>This is a good place to release any remaining resources, log final messages, or perform
     * any other cleanup that should occur after the actor has stopped processing messages. The
     * default implementation does nothing.
     */
    public void postStop() {
    }

    /**
     * Determines the failure strategy to be applied when an exception is thrown within the actor.
     *
     * @param throwable the Throwable that was thrown, representing the error or exception
     * @return the {@code FailureStrategy} to be used, which dictates how the actor should handle
     * the failure
     */
    public FailureStrategy failureStrategy(Throwable throwable) {
        if (throwable instanceof AssertionError) {
            return FailureStrategy.SYSTEM_ERROR;
        }
        return FailureStrategy.RESTART;
    }

    /**
     * Returns the message type that this actor is designed to handle.
     *
     * @return the {@code MsgType<T>} representing the type of messages this actor can process
     */
    public abstract MsgType<T> msgType();
}
