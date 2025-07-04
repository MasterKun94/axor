package io.axor.api.impl;

import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.ActorRefRich;
import io.axor.runtime.MsgType;
import io.axor.runtime.Serde;
import io.axor.runtime.StreamDefinition;
import io.axor.runtime.StreamManager;

public sealed abstract class AbstractActorRef<T> extends ActorRefRich<T>
        permits LocalActorRef, RemoteActorRef, NoSenderActorRef {

    private final ActorAddress address;
    private StreamDefinition<T> definition;
    private StreamManager<T> streamManager;

    protected AbstractActorRef(ActorAddress address) {
        this.address = address;
    }

    protected void initialize(Serde<T> serde,
                              StreamManager<T> streamManager) {
        this.definition = new StreamDefinition<>(address.streamAddress(), serde);
        this.streamManager = streamManager;
    }

    public StreamDefinition<T> getDefinition() {
        return definition;
    }

    public StreamManager<T> getStreamManager() {
        return streamManager;
    }

    protected void cleanup() {
        streamManager.close();
    }

    @Override
    public MsgType<T> msgType() {
        return definition.serde().getType();
    }

    @Override
    public ActorAddress address() {
        return address;
    }

    @Override
    protected abstract void tell0(T value, ActorRef<?> sender);

    @Override
    public abstract boolean isLocal();

}
