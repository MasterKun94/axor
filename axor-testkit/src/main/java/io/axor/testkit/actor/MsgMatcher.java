package io.axor.testkit.actor;

import io.axor.api.ActorRef;

public interface MsgMatcher<T> {
    boolean match(T msg, ActorRef<?> sender);
}
