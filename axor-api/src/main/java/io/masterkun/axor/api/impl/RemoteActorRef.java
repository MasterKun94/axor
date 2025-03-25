package io.masterkun.axor.api.impl;

import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorRefRich;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.api.SystemEvent;
import io.masterkun.axor.runtime.EventDispatcher;
import io.masterkun.axor.runtime.Serde;
import io.masterkun.axor.runtime.StreamManager;
import io.masterkun.axor.runtime.StreamOutChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private RemoteActorRef(ActorAddress address,
                           Serde<T> serde,
                           StreamManager<T> manager,
                           ActorSystem system) {
        super(address);
        this.system = system;
        initialize(serde, manager);
    }

    static <T> RemoteActorRef<T> create(ActorAddress address,
                                        Serde<T> serde,
                                        EventDispatcher executor,
                                        StreamOutChannel<T> channel,
                                        ActorSystem system) {
        StreamManager<T> manager = new StreamManager<>(channel, executor, t -> {
            throw new UnsupportedOperationException("Not supported");
        });
        return new RemoteActorRef<>(address, serde, manager, system);
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
            executor.execute(() -> tellAction.accept(manager, value));
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
