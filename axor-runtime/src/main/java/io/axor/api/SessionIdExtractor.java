package io.axor.api;

import io.axor.runtime.Signal;

public interface SessionIdExtractor<T> {
    long extractFromMsg(T msg, ActorRef<?> sender);

    long extractFromSignal(Signal signal);
}
