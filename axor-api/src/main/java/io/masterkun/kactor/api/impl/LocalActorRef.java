package io.masterkun.kactor.api.impl;

import io.masterkun.kactor.api.Actor;
import io.masterkun.kactor.api.ActorAddress;
import io.masterkun.kactor.api.ActorCreator;
import io.masterkun.kactor.api.ActorRef;
import io.masterkun.kactor.api.ActorRefRich;
import io.masterkun.kactor.api.ActorSystem;
import io.masterkun.kactor.api.SystemEvent;
import io.masterkun.kactor.config.ActorConfig;
import io.masterkun.kactor.exception.ActorException;
import io.masterkun.kactor.runtime.EventDispatcher;
import io.masterkun.kactor.runtime.SerdeRegistry;
import io.masterkun.kactor.runtime.Status;
import io.masterkun.kactor.runtime.StreamDefinition;
import io.masterkun.kactor.runtime.StreamInChannel;
import io.masterkun.kactor.runtime.StreamManager;
import io.masterkun.kactor.runtime.StreamManager.MsgHandler;
import io.masterkun.kactor.runtime.StreamOutChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.MDCAdapter;

import java.io.Closeable;
import java.util.function.BiConsumer;

/**
 * A concrete implementation of {@link AbstractActorRef} that represents a local actor reference.
 * This class is responsible for managing the lifecycle and message delivery to a local actor.
 * It ensures that messages are processed in the context of an event executor, allowing for
 * non-blocking and asynchronous message handling.
 *
 * <p>The {@code LocalActorRef} is created with an actor, an event executor, and other necessary
 * components. It handles the actor's start, stop, and restart processes, and it also manages
 * the communication channels for sending and receiving messages.
 *
 * @param <T> the type of messages that this actor can handle
 */
public final class LocalActorRef<T> extends AbstractActorRef<T> {
    private static final Logger LOG = LoggerFactory.getLogger(LocalActorRef.class);

    private final EventDispatcher executor;
    private final Actor<T> actor;
    private final Closeable unregisterHook;
    private final BiConsumer<ActorRef<?>, T> tellAction;
    private boolean stopped;

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
        BiConsumer<ActorRef<?>, T> tellAction = (sender1, msg) -> {
            ActorContextImpl<T> context = (ActorContextImpl<T>) actor.context();
            if (stopped) {
                context.deadLetter(msg);
                return;
            }
            try {
                context.sender(sender1);
                actor.onReceive(msg);
            } catch (Throwable e) {
                systemEvent(SystemEvent.ActorAction.ON_RECEIVE, e);
                try {
                    switch (actor.failureStrategy(e)) {
                        case RESTART -> internalRestart();
                        case STOP -> stop();
                        case RESUME -> {
                        }
                        default -> throw new IllegalArgumentException("unknown failure strategy");
                    }
                } catch (Throwable ex) {
                    context.system().systemFailure(ex);
                }
            } finally {
                context.sender(ActorRef.noSender());
            }
        };
        if (config.logger().mdcEnabled()) {
            String systemName = system.name();
            this.tellAction = (sender, msg) -> {
                MDCAdapter mdc = MDC.getMDCAdapter();
                mdc.put("system", systemName);
                mdc.put("actor", LocalActorRef.this.displayName());
                mdc.put("sender", ((ActorRefRich<?>) sender).displayName());
                try {
                    tellAction.accept(sender, msg);
                } finally {
                    mdc.remove("system");
                    mdc.remove("actor");
                    mdc.remove("sender");
                }
            };
        } else {
            this.tellAction = tellAction;
        }

        var definition = new StreamDefinition<>(address.streamAddress(), serdeRegistry.create(msgType));
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
                LocalActorRef.this.tellAction.accept(sender, msg);
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
                systemEvent(SystemEvent.ActorAction.ON_START, e);
                internalStop();
            }
        });
    }

    private void systemEvent(SystemEvent.ActorAction action, Throwable cause) {
        LOG.error("{} error during action: {}", this, action, cause);
        systemEvent(new SystemEvent.ActorError(this, action, cause));
    }

    private void systemEvent(SystemEvent event) {
        if (LOG.isDebugEnabled()) {
            switch (event) {
                case SystemEvent.ActorStarted(var a) -> LOG.debug("{} started", a);
                case SystemEvent.ActorStopped(var a) -> LOG.debug("{} stopped", a);
                case SystemEvent.ActorRestarted(var a) -> LOG.debug("{} restarted", a);
                default -> {
                }
            }
        }
        ((ActorSystemImpl) actor.context().system()).systemEvents().publishToAll(event, this);
    }

    private void internalRestart() {
        assert executor.inExecutor();
        try {
            actor.onRestart();
            systemEvent(new SystemEvent.ActorRestarted(this));
        } catch (Exception ex) {
            systemEvent(SystemEvent.ActorAction.ON_RESTART, ex);
            internalStop();
        }
    }

    private void internalStop() {
        assert executor.inExecutor();
        try {
            actor.preStop();
        } catch (Exception e) {
            systemEvent(SystemEvent.ActorAction.ON_PRE_STOP, e);
        }
        try {
            unregisterHook.close();
            cleanup();
        } catch (Throwable e) {
            // ignore TODO
        }
        try {
            stopped = true;
            actor.postStop();
        } catch (Throwable e) {
            systemEvent(SystemEvent.ActorAction.ON_POST_STOP, e);
        }
        systemEvent(new SystemEvent.ActorStopped(this));
    }

    void stop() {
        if (!executor.inExecutor()) {
            executor.execute(this::internalStop);
            return;
        }
        internalStop();
    }

    @Override
    public void tell(T value, ActorRef<?> sender) {
        executor.execute(() -> tellAction.accept(sender, value));
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    public boolean isStopped() {
        return stopped;
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
