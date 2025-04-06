package io.axor.runtime;

/**
 * A non-sealed interface that extends {@link StreamChannel} and is used to define a channel for
 * incoming streams. This interface provides a method to open a stream with a remote endpoint, using
 * an event executor and an observer.
 *
 * @param <IN> the type of the incoming messages
 */
public non-sealed interface StreamInChannel<IN> extends StreamChannel<IN> {

    /**
     * Opens a stream with the specified remote endpoint, using the provided event executor and
     * observer.
     *
     * @param <OUT>    the type of the outgoing messages
     * @param remote   the definition of the remote stream, including the address and serde for
     *                 serialization/deserialization
     * @param executor the event executor to be used for handling events related to the stream
     * @param observer the observer to be notified of signal and the end of the stream, including
     *                 any status or error
     * @return a {@link StreamObserver} that can be used to send incoming messages of type IN to the
     * remote endpoint
     */
    <OUT> StreamObserver<IN> open(StreamDefinition<OUT> remote,
                                  EventDispatcher executor,
                                  Observer observer);
}
