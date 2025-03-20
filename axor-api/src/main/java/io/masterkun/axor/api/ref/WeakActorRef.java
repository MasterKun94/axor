package io.masterkun.axor.api.ref;

import io.masterkun.axor.api.ActorRef;

public interface WeakActorRef<T, Q extends WeakActorRefQueue<?>> {
    ActorRef<T> get();

    boolean refersTo(ActorRef<T> actor);

    void clear();
}
