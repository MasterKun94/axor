package io.axor.api.impl;

import io.axor.api.ActorContext;
import io.axor.api.ActorCreator;
import io.axor.api.ActorRef;
import io.axor.api.ActorRefRich;
import io.axor.api.ActorSettings;
import io.axor.api.ActorSystem;
import io.axor.api.EventStageSignal;
import io.axor.api.Scheduler;
import io.axor.api.SystemEvent;
import io.axor.commons.concurrent.EventStage;
import io.axor.commons.concurrent.Try;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.MsgType;

import java.util.List;

import static io.axor.api.impl.ActorUnsafe.signal;

class ActorContextImpl<T> implements ActorContext<T> {
    private final ActorSystem system;
    private final EventDispatcher executor;
    private final LocalActorRef<T> self;
    private final ActorSettings settings = new ActorSettings();
    private final ActorSessionsV2<T> sessions;
    String mdcExtra;
    private ActorRef<?> sender;

    public ActorContextImpl(ActorSystem system, EventDispatcher executor, LocalActorRef<T> self) {
        this.system = system;
        this.executor = executor;
        this.self = self;
        this.sessions = new ActorSessionsV2<>(this);
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
    public Scheduler scheduler() {
        return system.getScheduler(executor);
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

    @Override
    public ActorSettings settings() {
        return settings;
    }

    public int state() {
        return self.getState();
    }

    @Override
    public ActorSessionsV2<T> sessions() {
        return sessions;
    }

    @Override
    public void signalWhenComplete(long tagId, EventStage<?> stage) {
        stage.observe(m -> signal(self, new EventStageSignal<>(tagId, Try.success(m))),
                e -> signal(self, new EventStageSignal<>(tagId, Try.failure(e))));
    }

    @Override
    public void mdcExtra(String value) {
        mdcExtra = value;
    }
}
