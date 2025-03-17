package io.masterkun.kactor.api.impl;

import io.masterkun.kactor.api.ActorAddress;
import io.masterkun.kactor.api.ActorRef;
import io.masterkun.kactor.runtime.MsgType;

import java.util.stream.Stream;

public interface ActorRefCache {
    ActorRef<?> get(ActorAddress address);

    <T> ActorRef<T> getOrCreate(ActorAddress address, MsgType<T> msgType);

    Stream<? extends RemoteActorRef<?>> getAll();

    void remove(ActorRef<?> actorRef);

    interface Factory {
        <T> RemoteActorRef<T> create(ActorAddress address, MsgType<T> msgType);
    }
}
