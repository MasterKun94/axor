package io.axor.testkit.actor;

import io.axor.api.ActorRef;


/**
 * An interface for asserting on messages and their senders/receivers in tests.
 *
 * This interface is used by MockActorRef to verify that messages match expected values
 * and are sent to or received from the expected actors.
 *
 * @param <T> The type of messages to assert on
 */
public interface MsgAssertion<T> {
    /**
     * Asserts that a message and its sender/receiver match expected values.
     *
     * @param msg The message to assert on
     * @param ref The sender or receiver of the message
     * @throws AssertionError if the assertion fails
     */
    void testAssert(T msg, ActorRef<?> ref) throws AssertionError;
}
