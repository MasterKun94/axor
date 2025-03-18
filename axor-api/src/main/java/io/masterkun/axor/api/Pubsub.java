package io.masterkun.axor.api;

import io.masterkun.axor.api.impl.LocalPubsub;
import io.masterkun.axor.runtime.EventDispatcher;
import io.masterkun.axor.runtime.EventDispatcherGroup;
import io.masterkun.axor.runtime.MsgType;

/**
 * Represents a publish-subscribe mechanism for distributing messages of type T to multiple
 * subscribers. Extends the functionality of {@link EventStream} by providing methods to publish
 * messages to all subscribed actors or send a message to a specific actor. This interface is
 * designed to work within an actor system, facilitating event-driven communication and decoupling
 * between components.
 *
 * @param <T> the type of the message that can be published and received through this pubsub
 *            mechanism
 */
public interface Pubsub<T> extends EventStream<T> {

    /**
     * Creates a new instance of {@link Pubsub} with the specified message type and actor system.
     * This method is a convenience wrapper that calls the full create method with logging for
     * unsendable messages enabled by default.
     *
     * @param <T>     the type of the message that can be published and received through this pubsub
     *                mechanism
     * @param system  the {@link ActorSystem} in which the pubsub will operate
     * @param msgType the {@link MsgType} representing the type of messages that will be handled by
     *                this pubsub
     * @return a new instance of {@link Pubsub} configured with the provided parameters and default
     * logging behavior
     */
    static <T> Pubsub<T> create(ActorSystem system, MsgType<T> msgType) {
        return create(system, msgType, true);
    }

    /**
     * Creates a new instance of {@link Pubsub} with the specified message type, actor system, and
     * logging behavior.
     *
     * @param <T>          the type of the message that can be published and received through this
     *                     pubsub mechanism
     * @param system       the {@link ActorSystem} in which the pubsub will operate
     * @param msgType      the {@link MsgType} representing the type of messages that will be
     *                     handled by this pubsub
     * @param logUnSendMsg a boolean indicating whether unsendable messages should be logged
     * @return a new instance of {@link Pubsub} configured with the provided parameters
     */
    static <T> Pubsub<T> create(ActorSystem system, MsgType<T> msgType, boolean logUnSendMsg) {
        EventDispatcherGroup executorGroup = system.getEventExecutorGroup();
        EventDispatcher executor = executorGroup.nextExecutor();
        return new LocalPubsub<>(msgType, executor, logUnSendMsg);
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
    void publishToAll(T msg, ActorRef<?> sender);

    /**
     * Sends the given message to a specific actor.
     *
     * <p>This method targets a single actor, identified by the provided {@code ActorRef}, and
     * sends
     * it the specified message. The sender of the message is also provided, which can be used by
     * the receiving actor to identify the origin of the message.
     *
     * @param msg    the message to be sent, of type T
     * @param sender the {@code ActorRef} representing the sender of the message
     */
    void sendToOne(T msg, ActorRef<?> sender);
}
