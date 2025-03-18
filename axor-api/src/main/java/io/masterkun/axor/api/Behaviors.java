package io.masterkun.axor.api;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Provides a set of static methods to create and manage actor behaviors. Behaviors are the core
 * components that define how actors process messages and can be used to implement various stateful
 * and stateless message handling strategies.
 *
 * <p>This class includes methods for creating predefined behaviors such as stopping an actor,
 * indicating that a message is unhandled, or keeping the current behavior. It also provides methods
 * to define custom behaviors using functional interfaces.
 */
public class Behaviors {

    /**
     * Returns a behavior that keeps the current behavior of an actor, effectively doing nothing and
     * allowing the actor to continue with its current state.
     *
     * @param <T> the type of messages this actor can handle
     * @return a {@code Behavior<T>} that represents the same behavior as the current one
     */
    @SuppressWarnings("unchecked")
    public static <T> Behavior<T> same() {
        return (Behavior<T>) BehaviorInternal.SAME;
    }

    /**
     * Returns a behavior that stops the actor, causing it to terminate and no longer process any
     * messages.
     *
     * @param <T> the type of messages this actor can handle
     * @return a {@code Behavior<T>} that represents the stop behavior, which will terminate the
     * actor
     */
    @SuppressWarnings("unchecked")
    public static <T> Behavior<T> stop() {
        return (Behavior<T>) BehaviorInternal.STOP;
    }

    /**
     * Returns a behavior that indicates the message is unhandled by the actor. This behavior can be
     * used to signify that the actor does not have a specific handler for the received message.
     *
     * @param <T> the type of messages this actor can handle
     * @return a {@code Behavior<T>} that represents the unhandled behavior, indicating the message
     * is not processed
     */
    @SuppressWarnings("unchecked")
    public static <T> Behavior<T> unhandled() {
        return (Behavior<T>) BehaviorInternal.UNHANDLED;
    }

    /**
     * Creates a behavior that processes a message using the provided handler.
     *
     * <p>This method takes a {@code BiFunction} that defines how an actor should handle a message.
     * The function is applied to the current {@code ActorContext} and the received message, and it
     * returns a new or updated behavior for the actor.
     *
     * @param <T>     the type of messages this actor can handle
     * @param handler a {@code BiFunction} that takes the current {@code ActorContext<T>} and the
     *                received message, and returns a new or updated {@code Behavior<T>}
     * @return a {@code Behavior<T>} that represents the behavior defined by the provided handler
     */
    public static <T> Behavior<T> receiveMessage(BiFunction<ActorContext<T>, T, Behavior<T>> handler) {
        return handler::apply;
    }

    /**
     * Creates a behavior that processes a message using the provided handler.
     *
     * <p>This method takes a {@code Function} that defines how an actor should handle a message.
     * The function is applied to the received message, and it returns a new or updated behavior for
     * the actor.
     *
     * @param <T>     the type of messages this actor can handle
     * @param handler a {@code Function} that takes the received message and returns a new or
     *                updated {@code Behavior<T>}
     * @return a {@code Behavior<T>} that represents the behavior defined by the provided handler
     */
    public static <T> Behavior<T> receiveMessage(Function<T, Behavior<T>> handler) {
        return (ctx, msg) -> handler.apply(msg);
    }
}
