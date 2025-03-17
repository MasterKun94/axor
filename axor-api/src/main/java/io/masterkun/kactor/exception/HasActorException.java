package io.masterkun.kactor.exception;

import java.io.IOException;

/**
 * Interface for exceptions that can provide an underlying {@link ActorException} as their cause.
 * This is useful in scenarios where a more specific exception, such as an {@link IOException},
 * is thrown due to an actor-related issue. Implementations of this interface should be able to
 * return the original {@code ActorException} that caused the issue.
 */
public interface HasActorException {
    ActorException getActorExceptionCause();
}
