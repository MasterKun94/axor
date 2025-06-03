package io.axor.testkit.actor;

import io.axor.api.ActorRef;

/**
 * An interface for making assertions about messages in actor-based tests.
 *
 * This interface is used to verify that messages received by mock actors
 * meet certain criteria. Implementations can check the content of the message,
 * the sender, or both.
 *
 * @param <T> The type of messages to assert on
 */
public interface MsgAssertion<T> {
    /**
     * Tests that a message and its sender meet the expected criteria.
     *
     * @param msg The message to test
     * @param sender The sender of the message
     * @throws AssertionError if the message or sender does not meet the expected criteria
     */
    void testAssert(T msg, ActorRef<?> sender) throws AssertionError;
}
