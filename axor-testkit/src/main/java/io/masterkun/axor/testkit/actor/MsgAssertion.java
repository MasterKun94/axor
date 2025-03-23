package io.masterkun.axor.testkit.actor;

import io.masterkun.axor.api.ActorRef;

public interface MsgAssertion<T> {
    void testAssert(T msg, ActorRef<?> sender) throws AssertionError;
}
