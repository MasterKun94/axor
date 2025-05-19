package io.axor.api;

import io.axor.api.impl.LocalPubsub;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.MsgType;

/**
 * Represents a publish-subscribe mechanism for distributing messages of type T to multiple
 * subscribers. Extends the functionality of {@link Eventbus} by providing methods to publish
 * messages to all subscribed actors or send a message to a specific actor. This interface is
 * designed to work within an actor system, facilitating event-driven communication and decoupling
 * between components.
 *
 * @param <T> the type of the message that can be published and received through this pubsub
 *            mechanism
 */
public interface Pubsub<T> extends Eventbus<T> {

    /**
     * Retrieves a {@code Pubsub} instance for the specified message type within the given actor
     * system.
     *
     * @param <T>     the type of the message that can be published and received through this pubsub
     *                mechanism
     * @param name    the name of the pubsub instance to retrieve
     * @param msgType the message type, which describes the type of messages that will be published
     *                and received
     * @param system  the actor system in which the pubsub instance will operate
     * @return a {@code Pubsub<T>} instance configured with the specified name, message type, and
     * actor system
     */
    static <T> Pubsub<T> get(String name, MsgType<T> msgType, ActorSystem system) {
        return LocalPubsub.get(name, msgType, system);
    }

    /**
     * Retrieves a {@code Pubsub} instance for the specified message type within the given actor
     * system.
     *
     * @param <T>          the type of the message that can be published and received through this
     *                     pubsub mechanism
     * @param name         the name of the pubsub instance to retrieve
     * @param msgType      the message type, which describes the type of messages that will be
     *                     published and received
     * @param logUnSendMsg a boolean flag indicating whether to log messages that could not be sent
     * @param system       the actor system in which the pubsub instance will operate
     * @return a {@code Pubsub<T>} instance configured with the specified name, message type, and
     * actor system
     */
    static <T> Pubsub<T> get(String name, MsgType<T> msgType, boolean logUnSendMsg,
                             ActorSystem system) {
        return LocalPubsub.get(name, msgType, logUnSendMsg, system);
    }

    /**
     * Publishes the given message to all subscribed actors.
     *
     * <p>This method sends the specified message to all actors that are currently subscribed to
     * the pubsub mechanism. The sender of the message is also provided, which can be used by the
     * receiving actors to identify the origin of the message.
     *
     * @param msg    the message to be published, of type T
     * @param sender the {@code ActorRef} representing the sender of the message
     */
    default void publishToAll(T msg, ActorRef<?> sender) {
        mediator().tell(new PublishToAll<>(msg), sender);
    }

    /**
     * Publishes the given message to all subscribed actors.
     *
     * <p>This method sends the specified message to all actors that are currently subscribed to
     * the pubsub mechanism. It uses a default sender, which is typically used when there is no
     * specific sender for the message.
     *
     * @param msg the message to be published, of type T
     */
    default void publishToAll(T msg) {
        publishToAll(msg, ActorRef.noSender());
    }

    /**
     * Sends the given message to a specific actor.
     *
     * <p>This method targets a single actor, identified by the provided {@code ActorRef}, and
     * sends it the specified message. The sender of the message is also provided, which can be used
     * by the receiving actor to identify the origin of the message.
     *
     * @param msg    the message to be sent, of type T
     * @param sender the {@code ActorRef} representing the sender of the message
     */
    default void sendToOne(T msg, ActorRef<?> sender) {
        mediator().tell(new SendToOne<>(msg), sender);
    }

    /**
     * Sends the given message to a specific actor, using a default sender.
     *
     * <p>This method targets a single actor and sends it the specified message. The sender of the
     * message is set to a default value, which is typically used when there is no specific sender
     * for the message.
     *
     * @param msg the message to be sent, of type T
     */
    default void sendToOne(T msg) {
        sendToOne(msg, ActorRef.noSender());
    }

    /**
     * Provides an {@link EventDispatcher} for managing and executing tasks, handling timeouts, and
     * checking if the current thread is within the executor.
     *
     * @return the {@link EventDispatcher} associated with this pubsub instance
     */
    default EventDispatcher dispatcher() {
        return ((ActorRefRich<?>) mediator()).dispatcher();
    }

    /**
     * Provides the {@code ActorRef} for the mediator actor that handles commands of type
     * {@code Command<T>}.
     *
     * @return an {@code ActorRef<Command<T>>} representing the mediator actor
     */
    @Override
    ActorRef<Command<T>> mediator();

    /**
     * A sealed interface that represents a command in the pubsub system. This interface is used to
     * define commands such as publishing messages to all subscribers or sending a message to a
     * specific actor.
     *
     * @param <T> the type of messages that the command operates on
     */
    sealed interface Command<T> permits Eventbus.Command, PublishToAll, SendToOne {
    }

    record PublishToAll<T>(T msg) implements Command<T> {
    }

    record SendToOne<T>(T msg) implements Command<T> {
    }
}
