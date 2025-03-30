package io.axor.runtime;

/**
 * Represents a channel for outgoing streams, extending the basic functionality of
 * {@link StreamChannel}. This interface provides a method to open a stream to a specified
 * destination, using an event executor and an observer.
 *
 * @param <IN> the type of the incoming data for the stream
 */
public non-sealed interface StreamOutChannel<IN> extends StreamChannel<IN> {

    /**
     * Opens a stream to the specified destination, using the provided event executor and observer.
     *
     * @param <OUT>    the type of the outgoing data for the stream
     * @param to       the definition of the stream, including the address and
     *                 serialization/deserialization strategy
     * @param executor the event executor to use for handling events related to the stream
     * @param observer the observer to notify of stream events, such as completion or error
     * @return a {@link StreamObserver} that can be used to send data to the opened stream
     */
    <OUT> StreamObserver<OUT> open(StreamDefinition<OUT> to,
                                   EventDispatcher executor,
                                   Observer observer);
}
