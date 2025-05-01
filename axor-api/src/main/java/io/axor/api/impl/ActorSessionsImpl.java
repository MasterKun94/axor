package io.axor.api.impl;

import io.axor.api.ActorAddress;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.ActorSessions;
import io.axor.api.ErrorMsgException;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;
import io.axor.runtime.MsgType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

public class ActorSessionsImpl<T> implements ActorSessions<T> {
    private final ActorContext<T> context;
    private final Map<ActorAddress, List<AskCtx<? extends T>>> pendingAsks = new HashMap<>();

    public ActorSessionsImpl(ActorContext<T> context) {
        this.context = context;
    }

    @Override
    public ActorContext<T> context() {
        return context;
    }

    @Override
    public <P extends T> EventStage<P> expectReceive(ActorRef<?> from, MsgType<? extends P> msgType, Predicate<P> isMsg, Duration timeout) {
        var ctx = new AskCtx<>(msgType, isMsg, context.dispatcher().newPromise());
        List<AskCtx<? extends T>> list = pendingAsks
                .computeIfAbsent(from.address(), k -> new ArrayList<>());
        list.add(ctx);
        var scheduleFuture = context.dispatcher().schedule(() -> {
            list.remove(ctx);
            ctx.promise.failure(new TimeoutException());
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        ctx.promise.observe((t, e) -> scheduleFuture.cancel(false));
        return ctx.promise;
    }

    boolean maybeNotify(T msg, ActorRef<?> sender) {
        if (pendingAsks.isEmpty()) {
            return false;
        }
        List<AskCtx<? extends T>> list = pendingAsks.get(sender.address());
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (var iterator = list.iterator(); iterator.hasNext(); ) {
            AskCtx<? extends T> ctx = iterator.next();
            if (ctx.maybeNotify(msg)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    private record AskCtx<T>(
            MsgType<? extends T> msgType,
            Predicate<T> isMsg,
            EventPromise<T> promise) {

        public boolean maybeNotify(Object msg) {
            if (msgType.isSupport(msg.getClass())) {
                @SuppressWarnings("unchecked") T cast = (T) msg;
                if (isMsg.test(cast)) {
                    promise.success(cast);
                    return true;
                }
            }
            return false;
        }
    }
}
