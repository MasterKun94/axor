package io.masterkun.axor.api.impl;

import io.masterkun.axor.api.Actor;
import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorCreator;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorRefRich;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.api.InternalSignals;
import io.masterkun.axor.api.Signal;
import io.masterkun.axor.api.SystemEvent;
import io.masterkun.axor.api.SystemEvent.ActorStopped;
import io.masterkun.axor.config.ActorConfig;
import io.masterkun.axor.exception.ActorException;
import io.masterkun.axor.runtime.EventDispatcher;
import io.masterkun.axor.runtime.SerdeRegistry;
import io.masterkun.axor.runtime.Status;
import io.masterkun.axor.runtime.StreamDefinition;
import io.masterkun.axor.runtime.StreamInChannel;
import io.masterkun.axor.runtime.StreamManager;
import io.masterkun.axor.runtime.StreamManager.MsgHandler;
import io.masterkun.axor.runtime.StreamOutChannel;
import io.masterkun.stateeasy.concurrent.EventPromise;
import io.masterkun.stateeasy.concurrent.EventStage;
import io.masterkun.stateeasy.concurrent.Try;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.MDCAdapter;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A concrete implementation of {@link AbstractActorRef} that represents a local actor reference.
 * This class is responsible for managing the lifecycle and message delivery to a local actor. It
 * ensures that messages are processed in the context of an event executor, allowing for
 * non-blocking and asynchronous message handling.
 *
 * <p>The {@code LocalActorRef} is created with an actor, an event executor, and other necessary
 * components. It handles the actor's start, stop, and restart processes, and it also manages the
 * communication channels for sending and receiving messages.
 *
 * @param <T> the type of messages that this actor can handle
 */
final class LocalActorRef<T> extends AbstractActorRef<T> {
    public static final int RUNNING_STATE = 0;
    public static final int STOPPING_STATE = 1;
    public static final int STOPPED_STATE = 2;
    private final Logger LOG = LoggerFactory.getLogger(LocalActorRef.class);
    private final EventDispatcher executor;
    private final Actor<T> actor;
    private final Closeable unregisterHook;
    private final BiConsumer<T, ActorRef<?>> tellAction;
    private final Consumer<Signal> signalAction;
    private Map<ActorAddress, ActorRef<?>> children;
    private List<Runnable> stopRunners;
    private Map<ActorAddress, WatcherHolder> watchers;
    private byte state = RUNNING_STATE;

    LocalActorRef(ActorAddress address, ActorSystem system, EventDispatcher executor,
                  ActorCreator<T> actorCreator, ActorConfig config) {
        super(address);
        this.executor = executor;
        this.actor = actorCreator.create(new ActorContextImpl<>(system, executor, this));
        var msgType = actor.msgType();
        SerdeRegistry serdeRegistry = system.getStreamServer().serdeRegistry();
        var serde = serdeRegistry.create(msgType);
        var streamDef = new StreamDefinition<>(address.streamAddress(), serde);
        var channel = new StreamOutChannel<T>() {
            final StreamOutChannel<T> delegate = system.getStreamServer().get(streamDef, executor);

            @Override
            public StreamDefinition<T> getSelfDefinition() {
                return delegate.getSelfDefinition();
            }

            @Override
            public <OUT> StreamObserver<OUT> open(StreamDefinition<OUT> to,
                                                  EventDispatcher executor,
                                                  Observer observer) {
                var remoteAddress = ActorAddress.create(to.address());
                var remoteMsgType = to.serde().getType();
                var selfAddress = LocalActorRef.this.address();
                var selfMsgType = LocalActorRef.this.msgType();
                systemEvent(new SystemEvent.StreamOutOpened(
                        remoteAddress, remoteMsgType,
                        selfAddress, selfMsgType));
                StreamObserver<OUT> streamOut = delegate.open(to, executor, observer);
                return new StreamObserver<>() {

                    @Override
                    public void onEnd(Status status) {
                        systemEvent(new SystemEvent.StreamOutClosed(
                                remoteAddress, remoteMsgType,
                                selfAddress, selfMsgType, status));
                        streamOut.onEnd(status);
                    }

                    @Override
                    public void onNext(OUT t) {
                        streamOut.onNext(t);
                    }
                };
            }
        };
        if (config.logger().mdcEnabled()) {
            Map<String, String> ctxMap = new HashMap<>(3);
            ctxMap.put("system", system.name());
            ctxMap.put("actor", ActorSystem.hasMultiInstance() ?
                    LocalActorRef.this.address().toString() :
                    LocalActorRef.this.displayName());
            this.tellAction = (msg, sender) -> {
                MDCAdapter mdc = MDC.getMDCAdapter();
                mdc.setContextMap(ctxMap);
                try {
                    doTell(msg, sender);
                } finally {
                    mdc.setContextMap(null);
                }
            };
            this.signalAction = signal -> {
                MDCAdapter mdc = MDC.getMDCAdapter();
                mdc.setContextMap(ctxMap);
                try {
                    doSignal(signal);
                } finally {
                    mdc.setContextMap(null);
                }
            };
        } else {
            this.tellAction = this::doTell;
            this.signalAction = this::doSignal;
        }

        var definition = new StreamDefinition<>(address.streamAddress(),
                serdeRegistry.create(msgType));
        var manager = new StreamManager<>(channel, executor, def -> new MsgHandler<>(def) {
            final ActorRef<?> sender = getSender();

            private ActorRef<?> getSender() {
                ActorAddress address = ActorAddress.create(getFrom().address());
                try {
                    return address.name().equals(NoSenderActorRef.ACTOR_NAME) ? system.noSender() :
                            system.get(address, getFrom().serde().getType());
                } catch (ActorException e) {
                    // TODO
                    throw new RuntimeException("maybe a bug", e);
                }
            }

            @Override
            public long id() {
                return ((ActorRefRich<?>) sender).getStreamManager().id();
            }

            @Override
            public void handle(T msg) {
                LocalActorRef.this.tellAction.accept(msg, sender);
            }
        });
        initialize(serde, manager);
        var streamInChannel = new ActorStreamInChannel(definition, manager);
        unregisterHook = system.getStreamServer().register(streamInChannel, executor);
        executor.execute(() -> {
            try {
                actor.onStart();
                systemEvent(new SystemEvent.ActorStarted(this));
            } catch (Throwable e) {
                systemErrorEvent(SystemEvent.ActorAction.ON_START, e);
                doStop(EventPromise.noop(executor));
            }
        });
    }

    private void systemErrorEvent(SystemEvent.ActorAction action, Throwable cause) {
        LOG.error("{} error during action: {}", this, action, cause);
        systemEvent(new SystemEvent.ActorError(this, action, cause));
    }

    private void systemEvent(SystemEvent event) {
        if (LOG.isDebugEnabled()) {
            switch (event) {
                case SystemEvent.ActorStarted(var a) -> LOG.debug("{} started", a);
                case ActorStopped(var a) -> LOG.debug("{} stopped", a);
                case SystemEvent.ActorRestarted(var a) -> LOG.debug("{} restarted", a);
                default -> {
                }
            }
        }
        ((ActorSystemImpl) actor.context().system()).systemEvents().publishToAll(event, this);
        if (watchers != null) {
            for (var entry : watchers.entrySet()) {
                maybeSignalWatcher(event, entry.getKey(), entry.getValue());
            }
        }
    }

    private void maybeSignalWatcher(SystemEvent event, ActorAddress address, WatcherHolder holder) {
        ActorRef<?> watcher = holder.get();
        if (watcher == null) {
            watchers.remove(address, holder);
            return;
        }
        for (Class<?> watchEvent : holder.watchEvents) {
            if (watchEvent.isInstance(event)) {
                ActorUnsafe.signal(watcher, event);
                return;
            }
        }
    }

    private void doRestart() {
        assert executor.inExecutor();
        try {
            actor.onRestart();
            systemEvent(new SystemEvent.ActorRestarted(this));
        } catch (Exception ex) {
            systemErrorEvent(SystemEvent.ActorAction.ON_RESTART, ex);
            doStop(EventPromise.noop(executor));
        }
    }

    private void doStop(EventPromise<Void> promise) {
        assert executor.inExecutor();
        if (state != RUNNING_STATE) {
            LOG.warn("Stop already invoked");
            promise.failure(new RuntimeException("stop already invoked"));
            return;
        }
        state = STOPPING_STATE;
        try {
            actor.preStop();
        } catch (Exception e) {
            systemErrorEvent(SystemEvent.ActorAction.ON_PRE_STOP, e);
        }
        EventStage<Void> stage = EventStage.succeed(null, executor);
        if (children != null) {
            for (ActorRef<?> child : children.values()) {
                stage = stage.flatTransform(t -> {
                    if (t.isFailure()) {
                        LOG.error("Unexpected error while stop child", t.cause());
                    }
                    return actor.context().system().stop(child);
                }, executor);
            }
        }
        stage.transform(t -> {
            if (t.isFailure()) {
                LOG.error("Unexpected error while stop child", t.cause());
            }
            try {
                unregisterHook.close();
                cleanup();
            } catch (Throwable e) {
                LOG.error("{} unexpected error on stopping", this, e);
            }
            if (stopRunners != null) {
                for (Runnable runnable : stopRunners) {
                    try {
                        runnable.run();
                    } catch (Throwable e) {
                        LOG.error("Stop runner throw unexpected error: " + e);
                    }
                }
                stopRunners = null;
            }
            systemEvent(new ActorStopped(this));
            if (watchers != null) {
                for (WatcherHolder holder : watchers.values()) {
                    try {
                        holder.release();
                    } catch (Throwable e) {
                        LOG.error("Release throw unexpected error: " + e);
                    }
                }
                watchers = null;
            }
            try {
                state = STOPPED_STATE;
                actor.postStop();
            } catch (Throwable e) {
                systemErrorEvent(SystemEvent.ActorAction.ON_POST_STOP, e);
            }
            return Try.success((Void) null);
        }, executor).addListener(promise);
    }

    @Internal
    void stop(EventPromise<Void> promise) {
        if (!executor.inExecutor()) {
            executor.execute(() -> doStop(promise));
            return;
        }
        doStop(promise);
    }

    @Internal
    Actor<T> getActor() {
        return actor;
    }

    @Override
    public void tell(T value, ActorRef<?> sender) {
        executor.execute(() -> tellAction.accept(value, sender));
    }

    @Override
    public void tellInline(T value, ActorRef<?> sender) {
        if (executor.inExecutor()) {
            tellAction.accept(value, sender);
        } else {
            tell(value, sender);
        }
    }

    private void doTell(T msg, ActorRef<?> sender1) {
        assert executor.inExecutor();
        ActorContextImpl<T> context = (ActorContextImpl<T>) actor.context();
        if (state != RUNNING_STATE) {
            context.deadLetter(msg);
            return;
        }
        try {
            context.sender(sender1);
            actor.onReceive(msg);
        } catch (Throwable e) {
            systemErrorEvent(SystemEvent.ActorAction.ON_RECEIVE, e);
            try {
                switch (actor.failureStrategy(e)) {
                    case RESTART -> doRestart();
                    case STOP -> stop(EventPromise.noop(executor));
                    case RESUME -> {
                    }
                    case SYSTEM_ERROR -> context.system().systemFailure(e);
                    default -> throw new IllegalArgumentException("unknown failure strategy");
                }
            } catch (Throwable ex) {
                actor.context().system().systemFailure(ex);
            }
        } finally {
            context.sender(ActorRef.noSender());
        }
    }

    @Internal
    public void signal(Signal signal) {
        executor.execute(() -> signalAction.accept(signal));
    }

    public void signalInline(Signal signal) {
        if (executor.inExecutor()) {
            signalAction.accept(signal);
        } else {
            signal(signal);
        }
    }

    private void doSignal(Signal signal) {
        assert executor.inExecutor();
        if (state != RUNNING_STATE) {
            LOG.warn("Ignore signal because actor not running");
        }
        try {
            if (signal == InternalSignals.POISON_PILL) {
                stop(EventPromise.noop(executor));
                return;
            }
            if (children != null && signal instanceof ActorStopped(var stoppedActor)) {
                children.remove(stoppedActor.address(), stoppedActor);
            }
            actor.onSignal(signal);
        } catch (Throwable e) {
            systemErrorEvent(SystemEvent.ActorAction.ON_SIGNAL, e);
        }
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    public int getState() {
        return state;
    }

    @Override
    public void addWatcher(ActorRef<?> watcher,
                           List<Class<? extends SystemEvent>> watchEvents) {
        if (executor.inExecutor()) {
            if (state == STOPPED_STATE) {
                maybeSignalWatcher(new ActorStopped(this), watcher.address(),
                        new WatcherHolder(watcher, watchEvents, () -> {
                        }));
                return;
            }
            if (watchers == null) {
                watchers = new HashMap<>();
            }
            watchers.compute(watcher.address(), (k, v) -> {
                if (v == null) {
                    return new WatcherHolder(watcher, watchEvents, () -> removeWatcher(watcher));
                } else {
                    return v.addEvents(watchEvents);
                }
            });
        } else {
            executor.execute(() -> addWatcher(watcher, List.copyOf(watchEvents)));
        }
    }

    @Override
    public void removeWatcher(ActorRef<?> watcher) {
        if (executor.inExecutor()) {
            if (watchers == null) {
                return;
            }
            watchers.compute(watcher.address(), (k, v) -> {
                if (v == null || v.get() == null || v.get() == watcher) {
                    return null;
                }
                return v;
            });
        } else {
            executor.execute(() -> removeWatcher(watcher));
        }
    }

    @Internal
    void addChild(ActorRef<?> child) {
        if (children == null) {
            children = new HashMap<>();
        }
        children.put(child.address(), child);
        addWatcher(child, List.of(ActorStopped.class));
    }

    @Internal
    void addStopRunner(Runnable runnable) {
        if (executor.inExecutor()) {
            if (stopRunners == null) {
                stopRunners = new ArrayList<>();
            }
            stopRunners.add(runnable);
        } else {
            executor.execute(() -> addStopRunner(runnable));
        }
    }

    @Internal
    void removeStopRunner(Runnable runnable) {
        if (executor.inExecutor()) {
            if (stopRunners == null) {
                return;
            }
            stopRunners.remove(runnable);
        } else {
            executor.execute(() -> removeStopRunner(runnable));
        }
    }

    private static class WatcherHolder {
        private final ActorRef<?> watcher;
        private final Set<Class<? extends SystemEvent>> watchEvents;
        private final Runnable stopRunner;

        private WatcherHolder(ActorRef<?> watcher,
                              List<Class<? extends SystemEvent>> watchEvents,
                              Runnable stopRunner) {
            this.watcher = watcher;
            this.watchEvents = new HashSet<>(watchEvents);
            this.stopRunner = stopRunner;
            ActorUnsafe.runOnStop(watcher, stopRunner);
        }

        public ActorRef<?> get() {
            return watcher;
        }

        public WatcherHolder addEvents(List<Class<? extends SystemEvent>> watchEvents) {
            this.watchEvents.addAll(watchEvents);
            return this;
        }

        public void release() {
            ActorUnsafe.cancelRunOnStop(watcher, stopRunner);
        }
    }

    private final class ActorStreamInChannel implements StreamInChannel<T> {
        private final StreamDefinition<T> definition;
        private final StreamManager<T> streamManager;

        private ActorStreamInChannel(StreamDefinition<T> definition,
                                     StreamManager<T> streamManager) {
            this.definition = definition;
            this.streamManager = streamManager;
        }

        @Override
        public StreamDefinition<T> getSelfDefinition() {
            return definition;
        }

        @Override
        public <OUT> StreamObserver<T> open(StreamDefinition<OUT> to,
                                            EventDispatcher executor,
                                            Observer unaryOrObserver) {
            var remoteAddress = ActorAddress.create(to.address());
            var remoteMsgType = to.serde().getType();
            var selfAddress = LocalActorRef.this.address();
            var selfMsgType = LocalActorRef.this.msgType();
            systemEvent(new SystemEvent.StreamInOpened(
                    remoteAddress, remoteMsgType,
                    selfAddress, selfMsgType));
            StreamObserver<T> streamIn = streamManager.getStreamIn(to, unaryOrObserver);
            return new StreamObserver<>() {

                @Override
                public void onEnd(Status status) {
                    systemEvent(new SystemEvent.StreamInClosed(
                            remoteAddress, remoteMsgType,
                            selfAddress, selfMsgType, status));
                    streamIn.onEnd(status);
                }

                @Override
                public void onNext(T t) {
                    streamIn.onNext(t);
                }
            };
        }
    }
}
