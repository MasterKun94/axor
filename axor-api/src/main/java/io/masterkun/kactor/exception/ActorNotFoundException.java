package io.masterkun.kactor.exception;

import io.masterkun.kactor.api.ActorAddress;

/**
 * Exception thrown when an actor with a specified address is not found.
 * This exception extends the {@link ActorException} and is used to indicate that an operation
 * attempted on a non-existent actor.
 */
public final class ActorNotFoundException extends ActorException {
    private final ActorAddress actorAddress;

    public ActorNotFoundException(ActorAddress actorAddress) {
        super(actorAddress + " not found");
        this.actorAddress = actorAddress;
    }

    public ActorAddress getActorAddress() {
        return actorAddress;
    }
}
