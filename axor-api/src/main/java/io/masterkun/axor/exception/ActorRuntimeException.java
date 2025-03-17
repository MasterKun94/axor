package io.masterkun.axor.exception;

/**
 * An unchecked exception that is thrown when an actor-related operation fails unexpectedly.
 * This exception wraps either an {@link ActorException} or an {@link ActorIOException} as its cause.
 * It implements the {@link HasActorException} interface to provide a method for retrieving the underlying actor exception.
 */
public class ActorRuntimeException extends RuntimeException implements HasActorException {

    public ActorRuntimeException(ActorException cause) {
        super(cause);
    }

    public ActorRuntimeException(ActorIOException cause) {
        super(cause);
    }

    @Override
    public ActorException getActorExceptionCause() {
        Throwable cause = getCause();
        if (cause instanceof ActorException cast) {
            return cast;
        }
        if (cause instanceof ActorIOException cast) {
            return cast.getActorExceptionCause();
        }
        throw new RuntimeException("should never happen");
    }
}
