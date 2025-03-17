package io.masterkun.kactor.api;

/**
 * A functional interface for creating instances of actors. Implementations of this interface
 * are responsible for providing a new actor instance, given an {@code ActorContext}.
 *
 * <p>This interface is typically used in actor systems to allow for the creation of actors
 * with specific configurations or behaviors, based on the provided context.
 *
 * @param <T> the type of messages that the created actor can handle
 */
public interface ActorCreator<T> {
    /**
     * Creates a new actor instance with the provided context.
     *
     * @param actorContext the context in which the actor will operate, providing access to the actor system,
     *                     event executor, and methods to interact with other actors
     * @return a new instance of {@code Actor<T>} configured with the given context
     */
    Actor<T> create(ActorContext<T> actorContext);
}
