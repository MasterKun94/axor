package io.axor.api;

import io.axor.runtime.EventDispatcher;
import io.axor.runtime.Signal;
import io.axor.runtime.StreamDefinition;
import io.axor.runtime.StreamManager;

import java.util.List;
import java.util.Objects;

/**
 * An abstract, non-sealed class that extends the {@code ActorRef} interface and provides additional
 * functionality. This class is intended to be extended by concrete actor reference
 * implementations.
 *
 * @param <T> the type of message that the actor can receive
 */
public non-sealed abstract class ActorRefRich<T> implements ActorRef<T> {
    private String name;

    public abstract void addWatcher(ActorRef<?> watcher,
                                    List<Class<? extends SystemEvent>> watchEvents);

    public abstract void removeWatcher(ActorRef<?> watcher);

    public abstract StreamDefinition<T> getDefinition();

    public abstract StreamManager<T> getStreamManager();

    public final EventDispatcher dispatcher() {
        return getStreamManager().getExecutor();
    }

    protected abstract void cleanup();

    public final void tellInline(T value, ActorRef<?> sender) {
        ((ActorRefRich<?>) sender).notifySend(value, this);
        tellInline0(value, sender);
    }

    @Override
    public final void tell(T value) {
        tell(value, ActorRef.noSender());
    }

    @Override
    public final void tell(T value, ActorRef<?> sender) {
        ((ActorRefRich<?>) sender).notifySend(value, this);
        tell0(value, sender);
    }

    protected abstract void tell0(T value, ActorRef<?> sender);

    protected void tellInline0(T value, ActorRef<?> sender) {
        tell0(value, sender);
    }

    public void signalInline(Signal signal) {
        signal(signal);
    }

    public abstract void signal(Signal signal);

    public final String displayName() {
        if (name == null) {
            var address = address();
            name = (isLocal() ? (address.system() + "@:/" + address.name()) : address.toString());
        }
        return name;
    }

    /**
     * Performs an unsafe cast of this {@code ActorRefRich} to a new type parameter P. This method
     * is intended for advanced use cases where the type safety can be assured by other means. Use
     * with caution, as incorrect usage can lead to runtime errors.
     *
     * @param <P> the target type to which the actor reference is being cast
     * @return the same instance of {@code ActorRefRich}, but with a different type parameter P
     */
    @SuppressWarnings("unchecked")
    public final <P> ActorRefRich<P> unsafeCast() {
        return (ActorRefRich<P>) this;
    }

    /**
     * Notifies the sending of a message to the specified receiver.
     *
     * @param <P>      the type of the message being sent
     * @param msg      the message to be sent
     * @param receiver the actor reference of the receiver to whom the message is being sent
     */
    protected <P> void notifySend(P msg, ActorRef<P> receiver) {
    }

    @Override
    public String toString() {
        return "ActorRef[" + displayName() + "]";
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ActorRefRich<?> that = (ActorRefRich<?>) o;
        return Objects.equals(address(), that.address()) &&
               Objects.equals(msgType(), that.msgType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(address(), msgType());
    }
}
