package io.masterkun.kactor.api;

import io.masterkun.kactor.runtime.StreamDefinition;
import io.masterkun.kactor.runtime.StreamManager;

/**
 * An abstract, non-sealed class that extends the {@code ActorRef} interface and provides additional functionality.
 * This class is intended to be extended by concrete actor reference implementations.
 *
 * @param <T> the type of message that the actor can receive
 */
public non-sealed abstract class ActorRefRich<T> implements ActorRef<T> {
    private String name;

    public abstract StreamDefinition<T> getDefinition();

    public abstract StreamManager<T> getStreamManager();

    protected abstract void cleanup();

    public String displayName() {
        if (name == null) {
            var address = address();
            name = (isLocal() ? (address.system() + "." + address.name()) : address.toString());
        }
        return name;
    }

    /**
     * Performs an unsafe cast of this {@code ActorRefRich} to a new type parameter P.
     * This method is intended for advanced use cases where the type safety can be assured by other means.
     * Use with caution, as incorrect usage can lead to runtime errors.
     *
     * @param <P> the target type to which the actor reference is being cast
     * @return the same instance of {@code ActorRefRich}, but with a different type parameter P
     */
    @SuppressWarnings("unchecked")
    public <P> ActorRefRich<P> unsafeCast() {
        return (ActorRefRich<P>) this;
    }

    @Override
    public String toString() {
        return "ActorRef[" + displayName() + "]";
    }
}
