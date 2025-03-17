package io.masterkun.kactor.api;

import io.masterkun.kactor.runtime.MsgType;

/**
 * Represents a stream of events that can be subscribed to and unsubscribed from.
 * This interface allows actors to receive messages of type T by subscribing to the event stream.
 *
 * @param <T> the type of the message that can be published and received through this stream
 */
public interface EventStream<T> {
    /**
     * Subscribes the given actor reference to this event stream, allowing it to receive messages of type T.
     *
     * @param ref the {@code ActorRef} that will be subscribed to the event stream and will start receiving messages
     */
    void subscribe(ActorRef<? super T> ref);

    /**
     * Unsubscribes the given actor reference from this event stream, stopping it from receiving further messages of type T.
     *
     * @param ref the {@code ActorRef} that will be unsubscribed from the event stream and will stop receiving messages
     */
    void unsubscribe(ActorRef<? super T> ref);

    /**
     * Returns the message type that this event stream is designed to handle.
     *
     * @return the {@code MsgType<T>} representing the type of messages this event stream can process
     */
    MsgType<T> msgType();
}
