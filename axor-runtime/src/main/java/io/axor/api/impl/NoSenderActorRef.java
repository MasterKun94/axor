package io.axor.api.impl;

import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.SystemEvent;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.MsgType;
import io.axor.runtime.Serde;
import io.axor.runtime.Signal;
import io.axor.runtime.StreamDefinition;
import io.axor.runtime.StreamManager;
import io.axor.runtime.StreamOutChannel;
import io.axor.runtime.impl.NoopSerdeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class NoSenderActorRef extends AbstractActorRef<Object> {
    public static final String ACTOR_NAME = "no_sender";
    public static final MsgType<Object> MSG_TYPE = MsgType.of(Object.class);
    public static final Serde<Object> SERDE = new NoopSerdeFactory.NoopSerde<>(MSG_TYPE);
    private static final Logger LOG = LoggerFactory.getLogger(NoSenderActorRef.class);
    private static final ActorAddress ADDRESS = ActorAddress.create(
            "no_sender", "localhost", -1, ACTOR_NAME);
    private static final ActorRef<Object> INSTANCE = NoSenderActorRef.create();

    private NoSenderActorRef(StreamManager<Object> streamManager) {
        super(ADDRESS);
        initialize(SERDE, streamManager);
    }

    NoSenderActorRef(ActorAddress address,
                     StreamOutChannel<Object> channel,
                     EventDispatcher executor) {
        super(address);
        var manager = new StreamManager<>(channel, executor, definition ->
                new StreamManager.MsgHandler<>(definition) {
                    @Override
                    public void handle(Object msg) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void signal(Signal signal) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public long id() {
                        throw new UnsupportedOperationException();
                    }
                });
        initialize(SERDE, manager);
    }

    @SuppressWarnings("unchecked")
    public static <T> ActorRef<T> get() {
        return (ActorRef<T>) INSTANCE;
    }

    private static NoSenderActorRef create() {
        var manager = new StreamManager<>(new StreamOutChannel<>() {
            @Override
            public <OUT> StreamObserver<OUT> open(StreamDefinition<OUT> to,
                                                  EventDispatcher executor,
                                                  Observer observer) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StreamDefinition<Object> getSelfDefinition() {
                return new StreamDefinition<>(ADDRESS.streamAddress(), SERDE);
            }
        }, null, null);
        return new NoSenderActorRef(manager);
    }

    @Override
    public void tell(Object value, ActorRef<?> sender) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("{} is telling message {} to NoSenderActor", sender, value);
        }
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public boolean isNoSender() {
        return true;
    }

    @Override
    public void addWatcher(ActorRef<?> watcher,
                           List<Class<? extends SystemEvent>> watchEvents) {
        LOG.warn("You are watching a noSender actor, this take no action");
    }

    @Override
    public void removeWatcher(ActorRef<?> watcher) {

    }

    @Override
    public void signal(Signal signal) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Signaling {} to NoSenderActor", signal);
        }
    }
}
