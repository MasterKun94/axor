package io.axor.api.impl;

import io.axor.api.Actor;
import io.axor.api.ActorAddress;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.ActorRefRich;
import io.axor.api.ActorSystem;
import io.axor.api.Pubsub;
import io.axor.api.SystemCacheKey;
import io.axor.commons.task.DependencyTask;
import io.axor.exception.ActorRuntimeException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.MsgType;
import io.axor.runtime.Unsafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A local implementation of the {@link Pubsub} interface, designed to handle message distribution
 * within a single JVM. This class manages a list of subscribed actors and provides methods to
 * publish messages to all subscribers or to a single subscriber. The class ensures thread safety by
 * using an {@link EventDispatcher} to manage concurrent access.
 *
 * @param <T> the type of the message that can be published and received through this pubsub
 *            mechanism
 */
public class LocalPubsub<T> implements Pubsub<T> {
    private static final Logger LOG = LoggerFactory.getLogger(LocalPubsub.class);
    private static final Map<SystemCacheKey, LocalPubsub<?>> CACHE = new ConcurrentHashMap<>();
    private final String name;
    private final MsgType<T> msgType;
    private final ActorRef<Command<T>> mediator;
    private final boolean logUnSendMsg;
    private volatile boolean closed;

    LocalPubsub(String name, MsgType<T> msgType, boolean logUnSendMsg,
                ActorSystem system) {
        this.name = name;
        this.msgType = msgType;
        this.logUnSendMsg = logUnSendMsg;
        system.shutdownHooks().register(new DependencyTask("pubsub-" + name, "root") {
            @Override
            public CompletableFuture<Void> run() {
                closed = true;
                return system.stop(mediator).toCompletableFuture();
            }
        });
        this.mediator = system.start(
                c -> new PubsubMediator<>(c, logUnSendMsg, msgType, this),
                "sys/PubsubMediator/" + name);
    }

    public static <T> LocalPubsub<T> get(String name, MsgType<T> msgType, ActorSystem system) {
        return get(name, msgType, true, system);
    }

    @SuppressWarnings("unchecked")
    public static <T> LocalPubsub<T> get(String name, MsgType<T> msgType, boolean logUnSendMsg,
                                         ActorSystem system) {
        return (LocalPubsub<T>) CACHE.compute(new SystemCacheKey(name, system), (k, v) -> {
            if (v != null) {
                if (v.msgType().equals(msgType)) {
                    return v;
                } else {
                    throw new ActorRuntimeException(new IllegalMsgTypeException(v.msgType(),
                            msgType));
                }
            } else {
                return new LocalPubsub<>(name, msgType, logUnSendMsg, system);
            }
        });
    }

    @Override
    public void publishToAll(T msg, ActorRef<?> sender) {
        if (closed) {
            if (logUnSendMsg) {
                LOG.warn("{} ignore publish msg because already closed", this);
            }
            return;
        }
        Pubsub.super.publishToAll(msg, sender);
    }

    @Override
    public void sendToOne(T msg, ActorRef<?> sender) {
        if (closed) {
            if (logUnSendMsg) {
                LOG.warn("{} ignore send msg because already closed", this);
            }
            return;
        }
        Pubsub.super.sendToOne(msg, sender);
    }

    @Override
    public EventDispatcher dispatcher() {
        return ((ActorRefRich<?>) mediator).dispatcher();
    }

    @Override
    public MsgType<T> msgType() {
        return msgType;
    }

    @Override
    public ActorRef<Command<T>> mediator() {
        return mediator;
    }

    @Override
    public String toString() {
        return "LocalPubsub[" +
               "name=" + name + ", " +
               "msgType=" + msgType.name() +
               ']';
    }

    private static class PubsubMediator<T> extends Actor<Command<T>> {
        private final Map<ActorAddress, ActorRef<? super T>> actors = new HashMap<>();
        private final List<ActorRef<? super T>> sendList = new ArrayList<>();
        private final boolean logUnSendMsg;
        private final MsgType<T> elemType;
        private final LocalPubsub<T> pubsub;
        private int sendOffset = 0;

        protected PubsubMediator(ActorContext<Command<T>> context, boolean logUnSendMsg,
                                 MsgType<T> elemType, LocalPubsub<T> pubsub) {
            super(context);
            this.logUnSendMsg = logUnSendMsg;
            this.elemType = elemType;
            this.pubsub = pubsub;
        }

        @Override
        public void onReceive(Command<T> pMsg) {
            switch (pMsg) {
                case PublishToAll<T>(var msg) -> doPublishToAll(msg);
                case SendToOne<T>(var msg) -> doSendToOne(msg);
                case Subscribe<T>(var ref) -> doSubscribe(ref);
                case Unsubscribe<T>(var ref) -> doUnsubscribe(ref);
                case null, default ->
                        throw new IllegalArgumentException("unsupported msg: " + pMsg);
            }
        }

        private void doSubscribe(ActorRef<? super T> ref) {
            if (actors.put(ref.address(), ref) == null) {
                sendList.add(ref);
                ActorUnsafe.signal(sender(), new SubscribeSuccess<>(self(), ref));
            } else {
                var alreadySubscribed = new IllegalArgumentException("already subscribed");
                ActorUnsafe.signal(sender(), new SubscribeFailed<>(self(), ref, alreadySubscribed));
            }
        }

        private void doUnsubscribe(ActorRef<? super T> ref) {
            ActorRef<? super T> removed = actors.remove(ref.address());
            if (removed != null) {
                sendList.remove(removed);
                Collections.shuffle(sendList);
                ActorUnsafe.signal(sender(), new UnsubscribeSuccess<>(self(), ref));
            } else {
                var notSubscribed = new IllegalArgumentException("subscription not found");
                ActorUnsafe.signal(sender(), new UnsubscribeFailed<>(self(), ref, notSubscribed));
            }
        }

        private void doPublishToAll(T msg) {
            if (actors.isEmpty()) {
                if (logUnSendMsg) {
                    LOG.warn("{} has no actors to publish for this message", this);
                }
                return;
            }
            for (Iterator<ActorRef<? super T>> iterator = sendList.iterator(); iterator.hasNext(); ) {
                ActorRef<? super T> actorRef = iterator.next();
                if (actorRef instanceof LocalActorRef<? super T> l && ActorUnsafe.isStopped(l)) {
                    iterator.remove();
                    LOG.warn("{} already stopped", actorRef);
                    continue;
                }
                actorRef.tell(msg, sender());
            }
        }

        private void doSendToOne(T msg) {
            if (actors.isEmpty()) {
                if (logUnSendMsg) {
                    LOG.warn("{} has no actors to send for this message", this);
                }
                return;
            }
            if (sendOffset >= sendList.size()) {
                sendOffset = 0;
            }
            sendList.get(sendOffset++).tell(msg, sender());
        }

        @Override
        public MsgType<Command<T>> msgType() {
            return Unsafe.msgType(Command.class, List.of(elemType));
        }
    }
}
