package io.axor.testkit.actor;

import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.ActorRefRich;
import io.axor.api.SystemEvent;
import io.axor.runtime.MsgType;
import io.axor.runtime.Signal;
import io.axor.runtime.StreamDefinition;
import io.axor.runtime.StreamManager;

import java.util.List;

/**
 * A no-operation implementation of ActorRef for testing purposes.
 *
 * This class provides an actor reference that doesn't perform any real actions
 * when messages or signals are sent to it. It's primarily used as a placeholder
 * in tests where the actual behavior of the actor is not important.
 *
 * @param <T> The type of messages this actor reference can receive
 */
public class NoopActorRef<T> extends ActorRefRich<T> {

    /** The address of this actor reference */
    private final ActorAddress address;

    /** The message type this actor reference handles */
    private final MsgType<T> msgType;

    /**
     * Creates a new NoopActorRef with the specified address and message type.
     *
     * @param address The actor address
     * @param msgType The message type
     */
    public NoopActorRef(ActorAddress address, MsgType<T> msgType) {
        this.address = address;
        this.msgType = msgType;
    }

    /**
     * Returns the message type this actor reference handles.
     *
     * @return The message type
     */
    @Override
    public MsgType<? super T> msgType() {
        return msgType;
    }

    /**
     * Returns the address of this actor reference.
     *
     * @return The actor address
     */
    @Override
    public ActorAddress address() {
        return address;
    }

    /**
     * No-operation implementation of tell.
     * This method does nothing when a message is sent to this actor reference.
     *
     * @param value The message to send
     * @param sender The sender of the message
     */
    @Override
    public void tell(T value, ActorRef<?> sender) {
        // No operation
    }

    /**
     * Returns whether this actor reference is local.
     * Always returns true for NoopActorRef.
     *
     * @return true
     */
    @Override
    public boolean isLocal() {
        return true;
    }

    /**
     * No-operation implementation of addWatcher.
     * This method does nothing when a watcher is added to this actor reference.
     *
     * @param watcher The actor reference to add as a watcher
     * @param watchEvents The events to watch for
     */
    @Override
    public void addWatcher(ActorRef<?> watcher, List<Class<? extends SystemEvent>> watchEvents) {
        // No operation
    }

    /**
     * No-operation implementation of removeWatcher.
     * This method does nothing when a watcher is removed from this actor reference.
     *
     * @param watcher The actor reference to remove as a watcher
     */
    @Override
    public void removeWatcher(ActorRef<?> watcher) {
        // No operation
    }

    /**
     * Returns the stream definition for this actor reference.
     * This operation is not supported by NoopActorRef.
     *
     * @return Never returns
     * @throws UnsupportedOperationException always
     */
    @Override
    public StreamDefinition<T> getDefinition() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the stream manager for this actor reference.
     * This operation is not supported by NoopActorRef.
     *
     * @return Never returns
     * @throws UnsupportedOperationException always
     */
    @Override
    public StreamManager<T> getStreamManager() {
        throw new UnsupportedOperationException();
    }

    /**
     * No-operation implementation of cleanup.
     * This method does nothing when this actor reference is cleaned up.
     */
    @Override
    protected void cleanup() {
        // No operation
    }

    /**
     * No-operation implementation of signal.
     * This method does nothing when a signal is sent to this actor reference.
     *
     * @param signal The signal to send
     */
    @Override
    public void signal(Signal signal) {
        // No operation
    }
}
