package io.masterkun.axor.api.impl;

import io.masterkun.axor.api.ActorContext;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.runtime.EventDispatcher;

class ActorContextImpl<T> implements ActorContext<T> {
    private final ActorSystem system;
    private final EventDispatcher executor;
    private final ActorRef<T> self;
    private ActorRef<?> sender;

    public ActorContextImpl(ActorSystem system, EventDispatcher executor, ActorRef<T> self) {
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
}
