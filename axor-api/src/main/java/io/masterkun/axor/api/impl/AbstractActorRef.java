package io.masterkun.axor.api.impl;

import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorRefRich;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.Serde;
import io.masterkun.axor.runtime.StreamDefinition;
import io.masterkun.axor.runtime.StreamManager;

public sealed abstract class AbstractActorRef<T> extends ActorRefRich<T> implements Watchable
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
    public abstract void tell(T value, ActorRef<?> sender);

    @Override
    public abstract boolean isLocal();

}
