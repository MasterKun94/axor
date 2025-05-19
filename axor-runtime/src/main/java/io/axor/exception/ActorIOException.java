package io.axor.exception;

import java.io.IOException;

/**
 * An exception that extends {@link IOException} and is thrown when an I/O error occurs due to an
 * actor-related issue. This exception implements the {@link HasActorException} interface, allowing
 * it to provide the underlying {@link ActorException} as its cause. This is useful in scenarios
 * where an I/O operation fails because of a problem with an actor, and the specific actor-related
 * cause needs to be preserved and accessible.
 */
public class ActorIOException extends IOException implements HasActorException {
    public ActorIOException(ActorException cause) {
        super(cause);
    }

    @Override
    public synchronized ActorException getCause() {
        return (ActorException) super.getCause();
    }

    @Override
    public ActorException getActorExceptionCause() {
        return getCause();
    }
}
