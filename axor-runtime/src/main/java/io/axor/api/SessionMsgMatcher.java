package io.axor.api;

public interface SessionMsgMatcher<T> {
    boolean matches(T msg, ActorRef<?> sender);
}
