package io.axor.testkit.actor;

import io.axor.api.ActorRef;

public interface MsgAssertion<T> {
    void testAssert(T msg, ActorRef<?> sender) throws AssertionError;
}
