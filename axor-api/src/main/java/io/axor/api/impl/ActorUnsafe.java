package io.axor.api.impl;

import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.ActorRefRich;
import io.axor.api.ActorSystem;
import io.axor.api.Signal;
import io.axor.runtime.EventDispatcher;

public class ActorUnsafe {
    public static boolean isStopped(ActorRef<?> ref) {
        if (ref instanceof LocalActorRef<?> l) {
            return l.getState() == LocalActorRef.STOPPED_STATE;
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

    public static void replaceCache(ActorSystem system, ActorRef<?> actor) {
        ((ActorSystemImpl) system).replaceCache(actor);
    }

    public static EventDispatcher getDispatcher(ActorRef<?> ref) {
        return ((ActorRefRich<?>) ref).getStreamManager().getExecutor();
    }

    public static <T> void tellInline(ActorRef<T> ref, T msg) {
        tellInline(ref, msg, ActorRef.noSender());
    }

    public static <T> void tellInline(ActorRef<T> ref, T msg, ActorRef<?> sender) {
        ((ActorRefRich<T>) ref).tellInline(msg, sender);
    }

    public static void signalInline(ActorRef<?> ref, Signal signal) {
        if (ref instanceof LocalActorRef<?> l) {
            l.signalInline(signal);
        } else if (ref instanceof ForwardingActorRef<?> f) {
            f.signalInline(signal);
        } else {
            throw new IllegalArgumentException("Not a LocalActorRef");
        }
    }

    public static boolean isStopInvoked(ActorContext<?> context) {
        return ((ActorContextImpl<?>) context).state() != LocalActorRef.RUNNING_STATE;
    }
}
