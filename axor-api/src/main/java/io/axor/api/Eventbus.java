package io.axor.api;

import io.axor.runtime.MsgType;

/**
 * An interface representing an event bus for managing subscriptions and unsubscriptions of actors.
 * The event bus allows actors to subscribe and unsubscribe to a specific type of message, and it
 * provides methods to interact with the mediator actor that handles these operations.
 *
 * @param <T> the type of messages that can be published and received through this event bus
 */
public interface Eventbus<T> {

    /**
     * Subscribes the given actor reference to receive messages of type T.
     * <p>
     * This method is a convenience overload that uses the provided actor reference as both the
     * subscriber and the sender for the subscription request. If the subscription is successful,
     * the {@code ref} will receive a {@link SubscribeSuccess} signal. If the subscription fails,
     * the {@code ref} will receive a {@link SubscribeFailed} signal.
     *
     * @param ref the {@code ActorRef} to subscribe, which must be able to receive messages of type
     *            T
     */
    default void subscribe(ActorRef<? super T> ref) {
        subscribe(ref, ref);
    }

    /**
     * Subscribes the given actor reference to receive messages of type T.
     * <p>
     * This method sends a subscription request to the mediator, indicating that the specified
     * {@code ref} should start receiving messages of type T. The sender of the subscription request
     * is also provided, which can be different from the subscribing actor. If the subscription is
     * successful, the {@code sender} will receive a {@link SubscribeSuccess} signal. If the
     * subscription fails, the {@code sender} will receive a {@link SubscribeFailed} signal.
     *
     * @param ref    the {@code ActorRef} to subscribe, which must be able to receive messages of
     *               type T
     * @param sender the {@code ActorRef} representing the sender of the subscription request
     */
    default void subscribe(ActorRef<? super T> ref, ActorRef<?> sender) {
        mediator().tell(new Subscribe<>(ref), sender);
    }

    /**
     * Unsubscribes the given actor reference from receiving messages of type T.
     * <p>
     * This method is a convenience overload that uses the provided actor reference as both the
     * unsubscribing actor and the sender of the unsubscription request. If the unsubscription is
     * successful, the {@code ref} will receive a {@link UnsubscribeSuccess} signal. If the
     * unsubscription fails, the {@code ref} will receive a {@link UnsubscribeFailed} signal.
     *
     * @param ref the {@code ActorRef} to unsubscribe, which must be currently subscribed to receive
     *            messages of type T
     */
    default void unsubscribe(ActorRef<? super T> ref) {
        unsubscribe(ref, ref);
    }

    /**
     * Unsubscribes the given actor reference from receiving messages of type T.
     * <p>
     * This method sends an unsubscription request to the mediator, indicating that the specified
     * {@code ref} should stop receiving messages of type T. The sender of the unsubscription
     * request is also provided, which can be different from the unsubscribing actor. If the
     * unsubscription is successful, the {@code sender} will receive a {@link UnsubscribeSuccess}
     * signal. If the unsubscription fails, the {@code sender} will receive a
     * {@link UnsubscribeFailed} signal.
     *
     * @param ref    the {@code ActorRef} to unsubscribe, which must be currently subscribed to
     *               receive messages of type T
     * @param sender the {@code ActorRef} representing the sender of the unsubscription request
     */
    default void unsubscribe(ActorRef<? super T> ref, ActorRef<?> sender) {
        mediator().tell(new Unsubscribe<>(ref), sender);
    }

    /**
     * Returns the message type associated with this event bus.
     *
     * @return the {@code MsgType} object representing the type of messages handled by this event
     * bus
     */
    MsgType<T> msgType();

    /**
     * Returns the mediator actor reference for this event bus. The mediator is responsible for
     * managing subscriptions and dispatching messages to subscribed actors.
     *
     * @return an {@code ActorRef} that can receive commands of type {@code Command<T>}, where T is
     * the message type associated with this event bus
     */
    ActorRef<? super Command<T>> mediator();

    /**
     * A sealed interface that represents a command in the event bus system, extending
     * {@link Pubsub.Command}. This interface is used to define commands such as subscribing and
     * unsubscribing actors from the event bus.
     *
     * @param <T> the type of messages that the command operates on
     */
    sealed interface Command<T> extends Pubsub.Command<T>
            permits Eventbus.Subscribe, Eventbus.Unsubscribe {
    }

    /**
     * The {@code Signal} interface represents a signal or event in the system, extending the
     * functionality of the base {@code io.axor.runtime.Signal} interface. Signals are used to
     * indicate various conditions, events, or actions that need to be handled by the system
     * components. Implementations of this interface can be used in actor frameworks, event-driven
     * systems, or any scenario where a standardized way of representing and handling signals is
     * required.
     *
     * <p>This sealed interface ensures that only specific implementations, such as records or
     * enums, can extend it, providing a controlled and consistent way to define different types of
     * signals.
     *
     * @see io.axor.runtime.Signal
     */
    sealed interface Signal extends io.axor.runtime.Signal {
        ActorRef<?> mediator();

        ActorRef<?> subscriber();
    }

    record Subscribe<T>(ActorRef<? super T> ref) implements Command<T> {
    }

    record Unsubscribe<T>(ActorRef<? super T> ref) implements Command<T> {
    }

    record SubscribeSuccess<T>(ActorRef<? super Command<T>> mediator,
                               ActorRef<? super T> subscriber) implements Signal {
    }

    record SubscribeFailed<T>(ActorRef<? super Command<T>> mediator, ActorRef<? super T> subscriber,
                              Throwable cause) implements Signal {
    }

    record UnsubscribeSuccess<T>(ActorRef<? super Command<T>> mediator,
                                 ActorRef<? super T> subscriber) implements Signal {
    }

    record UnsubscribeFailed<T>(ActorRef<? super Command<T>> mediator,
                                ActorRef<? super T> subscriber, Throwable cause) implements Signal {
    }
}
