package io.axor.api;

import io.axor.runtime.Signal;

public interface SessionManager<T> {
    ActorContext<T> actorContext();

    long openSession(SessionDefCreator<T> creator);

    void closeSession(long sessionId);

    boolean sendMsgToAll(T msg, ActorRef<?> sender);

    boolean sendSignalToAll(Signal signal);

    boolean sendMsgToOne(T msg, ActorRef<?> sender);

    boolean sendSignalToOne(Signal signal);

    boolean publishMsgToAll(T msg, ActorRef<?> sender);

    boolean publishSignalToAll(Signal signal);
}
