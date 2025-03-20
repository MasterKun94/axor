package io.masterkun.axor.api.ref;

import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.impl.LocalActorRef;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class LocalWeakActorRef<T> implements WeakActorRef<T, LocalWeakActorRefQueue> {
    private static final VarHandle VALUE;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            VALUE = l.findVarHandle(LocalWeakActorRef.class, "ref", LocalActorRef.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final LocalWeakActorRefQueue refQueue;
    private final Runnable runnable = this::cleanup;
    private volatile LocalActorRef<T> ref;

    LocalWeakActorRef(LocalActorRef<T> ref, LocalWeakActorRefQueue refQueue) {
        this.ref = ref;
        this.refQueue = refQueue;
        ref.addStopRunner(runnable);
    }

    protected void cleanup() {
        if (VALUE.getAndSet(this, null) != null) {
            refQueue.enqueue(this);
        }
    }

    @Override
    public ActorRef<T> get() {
        return ref;
    }

    @Override
    public void clear() {
        ref.removeStopRunner(runnable);
        ref = null;
    }

    @Override
    public boolean refersTo(ActorRef<T> actor) {
        return ref == actor;
    }
}
