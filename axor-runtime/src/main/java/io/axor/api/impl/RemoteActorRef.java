package io.axor.api.impl;

import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.ActorRefRich;
import io.axor.api.ActorSystem;
import io.axor.api.SystemEvent;
import io.axor.runtime.EventContext;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.Serde;
import io.axor.runtime.Signal;
import io.axor.runtime.StreamChannel;
import io.axor.runtime.StreamManager;
import io.axor.runtime.StreamOutChannel;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Represents a reference to an actor that is located on a remote system. This class extends
 * {@link AbstractActorRef} and provides the necessary functionality to send messages to a remote
 * actor.
 *
 * <p>The {@code RemoteActorRef} uses a {@link StreamManager} to handle the
 * communication with the remote actor. It ensures that messages are sent asynchronously, respecting
 * the executor's context.
 *
 * @param <T> The type of messages that can be sent to this actor.
 */
final class RemoteActorRef<T> extends AbstractActorRef<T> {
    private final Logger LOG = LoggerFactory.getLogger(RemoteActorRef.class);
    private final ActorSystem system;
    private final BiConsumer<StreamManager<?>, T> tellAction =
            (manager, t) -> manager
                    .getStreamOut(getStreamManager()).onNext(t);
    private final BiConsumer<StreamManager<?>, Signal> signalAction =
            (manager, signal) -> {
                StreamChannel.Observer streamIn = manager.getStreamIn(getStreamManager());
                if (streamIn != null) {
                    streamIn.onSignal(signal);
                } else {
                    manager.getStreamOut(getStreamManager()).onSignal(signal);
                }
            };

    private RemoteActorRef(ActorAddress address,
                           Serde<T> serde,
                           EventDispatcher executor,
                           StreamOutChannel<T> channel,
                           ActorSystem system) {
        super(address);
        this.system = system;
        WeakReference<RemoteActorRef<?>> ref = new WeakReference<>(this);
        initialize(serde, new StreamManager<>(channel, executor,
                def -> msgHandler(ref)));
    }

    static <T> RemoteActorRef<T> create(ActorAddress address,
                                        Serde<T> serde,
                                        EventDispatcher executor,
                                        StreamOutChannel<T> channel,
                                        ActorSystem system) {
        return new RemoteActorRef<>(address, serde, executor, channel, system);
    }

    private static <T> StreamManager.MsgHandler<T> msgHandler(WeakReference<RemoteActorRef<?>> ref) {
        return new StreamManager.MsgHandler<>(null) {
            @Override
            public void handle(T msg) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void signal(Signal signal) {
                RemoteActorRef<?> get = ref.get();
                if (get != null) {
                    get.signal(signal);
                }
            }

            @Override
            public long id() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public void tell(T value, ActorRef<?> sender) {
        if (sender.isNoSender()) {
            sender = system.noSender();
        }
        var manager = ((ActorRefRich<?>) sender).getStreamManager();
        var executor = manager.getExecutor();
        if (executor.inExecutor()) {
            tellAction.accept(manager, value);
        } else {
            EventContext.current().execute(() -> tellAction.accept(manager, value), executor);
        }
    }

    @Internal
    @Override
    public void signal(Signal signal) {
        signal(signal, ActorRef.noSender());
    }

    public void signal(Signal signal, ActorRef<?> sender) {
        if (sender.isNoSender()) {
            sender = system.noSender();
        }
        var manager = ((ActorRefRich<?>) sender).getStreamManager();
        var executor = manager.getExecutor();
        if (executor.inExecutor()) {
            signalAction.accept(manager, signal);
        } else {
            EventContext.current().execute(() -> signalAction.accept(manager, signal), executor);
        }
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public void addWatcher(ActorRef<?> watcher,
                           List<Class<? extends SystemEvent>> watchEvents) {
        LOG.warn("Method RemoteActorRef.addWatcher is not supported yet");
        // TODO
    }

    @Override
    public void removeWatcher(ActorRef<?> watcher) {
        LOG.warn("Method RemoteActorRef.removeWatcher is not supported yet");
        // TODO
    }
}
