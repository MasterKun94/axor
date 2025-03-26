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
        return (Behavior<T>) InternalBehavior.SAME;
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
        return (Behavior<T>) InternalBehavior.STOP;
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
        return (Behavior<T>) InternalBehavior.UNHANDLED;
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

    /**
     * Creates a behavior that processes both messages and signals using the provided handlers.
     *
     * <p>This method takes two {@code BiFunction}s: one for handling messages and another for
     * handling signals. The message handler is applied to the current {@code ActorContext} and the
     * received message, and it returns a new or updated behavior for the actor. The signal handler
     * is applied to the current {@code ActorContext} and the received signal, and it also returns a
     * new or updated behavior for the actor.
     *
     * @param <T>           the type of messages this actor can handle
     * @param msgHandler    a {@code BiFunction} that takes the current {@code ActorContext<T>} and
     *                      the received message, and returns a new or updated {@code Behavior<T>}
     * @param signalHandler a {@code BiFunction} that takes the current {@code ActorContext<T>} and
     *                      the received signal, and returns a new or updated {@code Behavior<T>}
     * @return a {@code Behavior<T>} that represents the behavior defined by the provided message
     * and signal handlers
     */
    public static <T> Behavior<T> receive(BiFunction<ActorContext<T>, T, Behavior<T>> msgHandler,
                                          BiFunction<ActorContext<T>, Signal, Behavior<T>> signalHandler) {
        return new Behavior<>() {
            @Override
            public Behavior<T> onReceive(ActorContext<T> context, T message) {
                return msgHandler.apply(context, message);
            }

            @Override
            public Behavior<T> onSignal(ActorContext<T> context, Signal signal) {
                return signalHandler.apply(context, signal);
            }
        };
    }

    /**
     * Creates a behavior that processes messages and signals using the provided handlers.
     *
     * <p>This method takes two functions: one for handling messages and another for handling
     * signals. The message handler is applied to the received message, and it returns a new or
     * updated behavior for the actor. The signal handler is applied to the received signal, and it
     * also returns a new or updated behavior for the actor.
     *
     * @param <T>           the type of messages this actor can handle
     * @param msgHandler    a {@code Function} that takes the received message and returns a new or
     *                      updated {@code Behavior<T>}
     * @param signalHandler a {@code Function} that takes the received signal and returns a new or
     *                      updated {@code Behavior<T>}
     * @return a {@code Behavior<T>} that represents the behavior defined by the provided message
     * and signal handlers
     */
    public static <T> Behavior<T> receive(Function<T, Behavior<T>> msgHandler, Function<Signal,
            Behavior<T>> signalHandler) {
        return new Behavior<>() {
            @Override
            public Behavior<T> onReceive(ActorContext<T> context, T message) {
                return msgHandler.apply(message);
            }

            @Override
            public Behavior<T> onSignal(ActorContext<T> context, Signal signal) {
                return signalHandler.apply(signal);
            }
        };
    }

    public static <T> Behavior<T> composite(Behavior<T> msgBehavior, Behavior<T> signalBehavior) {
        if (msgBehavior instanceof CompositeBehavior<T> c) {
            msgBehavior = c.msgBehavior;
        } else if (msgBehavior instanceof InternalBehavior && msgBehavior != InternalBehavior.SAME) {
            throw new UnsupportedOperationException("CompositeBehavior does not support " +
                    "InternalBehavior." + msgBehavior);
        }
        if (signalBehavior instanceof CompositeBehavior<T> c) {
            signalBehavior = c.signalBehavior;
        } else if (msgBehavior instanceof InternalBehavior && msgBehavior != InternalBehavior.SAME) {
            throw new UnsupportedOperationException("CompositeBehavior does not support " +
                    "InternalBehavior." + msgBehavior);
        }
        return new CompositeBehavior<>(msgBehavior, signalBehavior);
    }

    record CompositeBehavior<T>(Behavior<T> msgBehavior, Behavior<T> signalBehavior)
            implements Behavior<T> {

        @Override
        public Behavior<T> onReceive(ActorContext<T> context, T message) {
            return msgBehavior.onReceive(context, message);
        }

        @Override
        public Behavior<T> onSignal(ActorContext<T> context, Signal signal) {
            return signalBehavior.onSignal(context, signal);
        }
    }
}
