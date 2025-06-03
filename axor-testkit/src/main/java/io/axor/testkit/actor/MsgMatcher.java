package io.axor.testkit.actor;

import io.axor.api.ActorRef;

/**
 * An interface for matching messages in actor-based tests.
 *
 * This interface is used to determine whether a message and its sender match
 * certain criteria. It's similar to MsgAssertion, but instead of throwing an
 * AssertionError, it returns a boolean indicating whether the match was successful.
 *
 * MsgMatcher is typically used with the MsgAssertions.test() methods to create
 * assertions based on custom matching logic.
 *
 * @param <T> The type of messages to match
 */
public interface MsgMatcher<T> {
    /**
     * Determines whether a message and its sender match the expected criteria.
     *
     * @param msg The message to match
     * @param sender The sender of the message
     * @return true if the message and sender match the criteria, false otherwise
     */
    boolean match(T msg, ActorRef<?> sender);
}
