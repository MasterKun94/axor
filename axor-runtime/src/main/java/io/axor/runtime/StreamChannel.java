package io.axor.runtime;

public sealed interface StreamChannel<T> permits StreamInChannel, StreamOutChannel {

    StreamDefinition<T> getSelfDefinition();

    interface Observer {
        void onEnd(Status status);

        void onSignal(Signal signal);
    }

    interface StreamObserver<T> extends Observer {

        void onNext(T t);

        void onEnd(Status status);
    }
}
