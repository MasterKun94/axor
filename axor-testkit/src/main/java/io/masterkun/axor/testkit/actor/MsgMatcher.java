package io.masterkun.axor.testkit.actor;

import io.masterkun.axor.api.ActorRef;

public interface MsgMatcher<T> {
    boolean match(T msg, ActorRef<?> sender);
}
