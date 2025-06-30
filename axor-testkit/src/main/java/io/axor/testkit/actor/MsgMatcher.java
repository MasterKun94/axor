package io.axor.testkit.actor;

import io.axor.api.ActorRef;


/**
 * An interface for matching messages and their senders/receivers in tests.
 *
 * This interface is used by MsgAssertions to create assertions that verify
 * messages match certain criteria. It provides a more flexible alternative
 * to direct equality checks.
 *
 * @param <T> The type of messages to match
 */
public interface MsgMatcher<T> {

    /**
     * Determines if a message and its sender/receiver match the expected criteria.
     *
     * @param msg The message to match
     * @param ref The sender or receiver of the message
     * @return true if the message and sender/receiver match the criteria, false otherwise
     */
    boolean match(T msg, ActorRef<?> ref);
}
