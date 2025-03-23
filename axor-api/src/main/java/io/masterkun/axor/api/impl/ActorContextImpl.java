package io.masterkun.axor.api.impl;

import io.masterkun.axor.api.ActorContext;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorRefRich;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.api.SystemEvent;
import io.masterkun.axor.runtime.EventDispatcher;

import java.util.List;

class ActorContextImpl<T> implements ActorContext<T> {
    private final ActorSystem system;
    private final EventDispatcher executor;
    private final LocalActorRef<T> self;
    private ActorRef<?> sender;

    public ActorContextImpl(ActorSystem system, EventDispatcher executor, LocalActorRef<T> self) {
        this.system = system;
        this.executor = executor;
        this.self = self;
    }

    @Override
    public ActorRef<?> sender() {
        return sender;
    }

    public void sender(ActorRef<?> sender) {
        this.sender = sender;
    }

    @Override
    public ActorRef<T> self() {
        return self;
    }

    @Override
    public ActorSystem system() {
        return system;
    }

    @Override
    public EventDispatcher executor() {
        return executor;
    }

    @Override
    public void watch(ActorRef<?> target, List<Class<? extends SystemEvent>> watchEvents) {
        ((ActorRefRich<?>) target).addWatcher(self, watchEvents);
    }

    @Override
    public void unwatch(ActorRef<?> target) {
        ((ActorRefRich<?>) target).removeWatcher(self);
    }
}
