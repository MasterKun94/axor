package io.masterkun.kactor.api.impl;

import io.masterkun.kactor.api.ActorAddress;
import io.masterkun.kactor.api.ActorRef;
import io.masterkun.kactor.api.ActorRefRich;
import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.Serde;
import io.masterkun.kactor.runtime.StreamDefinition;
import io.masterkun.kactor.runtime.StreamManager;

public sealed abstract class AbstractActorRef<T> extends ActorRefRich<T> permits LocalActorRef, RemoteActorRef, NoSenderActorRef {

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
