package io.masterkun.axor.api.impl;

import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.Signal;

public class LocalActorRefUnsafe {
    public static boolean isStopped(ActorRef<?> ref) {
        if (ref instanceof LocalActorRef<?> l) {
            return l.isStopped();
        } else if (ref instanceof ForwardingActorRef<?> f) {
            return isStopped(f.getDelegate());
        } else {
            throw new IllegalArgumentException("Not a LocalActorRef");
        }
    }

    public static void runOnStop(ActorRef<?> ref, Runnable runnable) {
        if (ref instanceof LocalActorRef<?> l) {
            l.addStopRunner(runnable);
        } else if (ref instanceof ForwardingActorRef<?> f) {
            runOnStop(f.getDelegate(), runnable);
        } else {
            throw new IllegalArgumentException("Not a LocalActorRef");
        }
    }

    public static void cancelRunOnStop(ActorRef<?> ref, Runnable runnable) {
        if (ref instanceof LocalActorRef<?> l) {
            l.removeStopRunner(runnable);
        } else if (ref instanceof ForwardingActorRef<?> f) {
            cancelRunOnStop(f.getDelegate(), runnable);
        } else {
            throw new IllegalArgumentException("Not a LocalActorRef");
        }
    }

    public static void signal(ActorRef<?> ref, Signal signal) {
        if (ref instanceof LocalActorRef<?> l) {
            l.signal(signal);
        } else if (ref instanceof ForwardingActorRef<?> f) {
            f.signal(signal);
        } else {
            throw new IllegalArgumentException("Not a LocalActorRef");
        }
    }
}
