package io.axor.api.impl;

import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.runtime.MsgType;

import java.util.stream.Stream;

public interface ActorRefCache {
    ActorRef<?> get(ActorAddress address);

    <T> ActorRef<T> getOrCreate(ActorAddress address, MsgType<T> msgType);

    Stream<? extends RemoteActorRef<?>> getAll();

    void remove(ActorRef<?> actorRef);

    void cleanup();

    interface Factory {
        <T> RemoteActorRef<T> create(ActorAddress address, MsgType<T> msgType);
    }
}
