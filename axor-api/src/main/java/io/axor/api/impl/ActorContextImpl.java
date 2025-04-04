package io.axor.api.impl;

import io.axor.api.ActorContext;
import io.axor.api.ActorCreator;
import io.axor.api.ActorRef;
import io.axor.api.ActorRefRich;
import io.axor.api.ActorSystem;
import io.axor.api.SystemEvent;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.MsgType;

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

    @Override
    public <P> ActorRef<P> sender(MsgType<P> checkedType) {
        return sender.cast(checkedType);
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
    public EventDispatcher dispatcher() {
        return executor;
    }

    @Override
    public <P> ActorRef<P> startChild(ActorCreator<P> creator, String name) {
        ActorRef<P> child = system.start(creator, name, executor);
        if (executor.inExecutor()) {
            self.addChild(child);
        } else {
            executor.execute(() -> self.addChild(child));
        }
        return child;
    }

    @Override
    public void watch(ActorRef<?> target, List<Class<? extends SystemEvent>> watchEvents) {
        ((ActorRefRich<?>) target).addWatcher(self, watchEvents);
    }

    @Override
    public void unwatch(ActorRef<?> target) {
        ((ActorRefRich<?>) target).removeWatcher(self);
    }

    public int state() {
        return self.getState();
    }
}
