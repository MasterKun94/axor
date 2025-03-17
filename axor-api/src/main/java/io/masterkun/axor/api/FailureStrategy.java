package io.masterkun.axor.api;

/**
 * Enumerates the strategies that an actor can adopt in response to a failure, such as an exception being thrown during message processing.
 *
 * <p>Each strategy defines a different way to handle the failure, allowing the actor system to recover or terminate the actor as needed.
 */
public enum FailureStrategy {
    RESTART,
    STOP,
    RESUME
}
