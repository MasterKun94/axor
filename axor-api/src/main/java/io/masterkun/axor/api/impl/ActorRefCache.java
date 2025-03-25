package io.masterkun.axor.api.impl;

import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.runtime.MsgType;

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
