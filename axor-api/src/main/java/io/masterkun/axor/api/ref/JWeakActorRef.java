package io.masterkun.axor.api.ref;

import io.masterkun.axor.api.ActorRef;

import java.lang.ref.WeakReference;

public final class JWeakActorRef<T> extends WeakReference<ActorRef<T>> implements WeakActorRef<T,
        JWeakActorRefQueue> {

    public JWeakActorRef(ActorRef<T> ref, JWeakActorRefQueue refQueue) {
        super(ref, refQueue.getQueue());
    }
}
