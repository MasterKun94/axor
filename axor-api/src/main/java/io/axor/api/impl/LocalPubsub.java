package io.axor.api.impl;

import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.api.Pubsub;
import io.axor.api.SystemCacheKey;
import io.axor.commons.task.DependencyTask;
import io.axor.exception.ActorRuntimeException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.MsgType;
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
    private final Map<ActorAddress, ActorRef<? super T>> actors = new HashMap<>();
    private final List<ActorRef<? super T>> sendList = new ArrayList<>();
    private final EventDispatcher executor;
    private final boolean logUnSendMsg;
    private int sendOffset = 0;
    private volatile boolean closed = false;

    LocalPubsub(String name, MsgType<T> msgType, EventDispatcher executor, boolean logUnSendMsg,
                ActorSystem system) {
        this.name = name;
        this.msgType = msgType;
        this.executor = executor;
        this.logUnSendMsg = logUnSendMsg;
        system.shutdownHooks().register(new DependencyTask("pubsub-" + name, "root") {
            @Override
            public CompletableFuture<Void> run() {
                closed = true;
                return CompletableFuture.completedFuture(null);
            }
        });
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
                EventDispatcher dispatcher = system.getDispatcherGroup().nextDispatcher();
                return new LocalPubsub<>(name, msgType, dispatcher, logUnSendMsg, system);
            }
        });
    }

    @Override
    public void subscribe(ActorRef<? super T> actor) {
        if (!executor.inExecutor()) {
            executor.execute(() -> subscribe(actor));
            return;
        }
        if (actor == null) {
            throw new NullPointerException("actor cannot be null");
        }
        if (actors.put(actor.address(), actor) == null) {
            sendList.add(actor);
        }
    }

    @Override
    public void unsubscribe(ActorRef<? super T> ref) {
        if (!executor.inExecutor()) {
            executor.execute(() -> unsubscribe(ref));
            return;
        }
        ActorRef<? super T> removed = actors.remove(ref.address());
        if (removed != null) {
            sendList.remove(removed);
            Collections.shuffle(sendList);
        }
    }

    @Override
    public void publishToAll(T msg, ActorRef<?> sender) {
        if (closed) {
            if (logUnSendMsg) {
                LOG.warn("{} ignore publish msg because already closed", this);
            }
            return;
        }
        if (executor.inExecutor()) {
            doPublishToAll(msg, sender);
        } else {
            executor.execute(() -> doPublishToAll(msg, sender));
        }
    }

    private void doPublishToAll(T msg, ActorRef<?> sender) {
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
            actorRef.tell(msg, sender);
        }
    }

    @Override
    public void sendToOne(T msg, ActorRef<?> sender) {
        if (closed) {
            if (logUnSendMsg) {
                LOG.warn("{} ignore send msg because already closed", this);
            }
            return;
        }
        if (executor.inExecutor()) {
            doSendToOne(msg, sender);
        } else {
            executor.execute(() -> doSendToOne(msg, sender));
        }
    }

    @Override
    public EventDispatcher dispatcher() {
        return executor;
    }

    private void doSendToOne(T msg, ActorRef<?> sender) {
        if (actors.isEmpty()) {
            if (logUnSendMsg) {
                LOG.warn("{} has no actors to send for this message", this);
            }
            return;
        }
        if (sendOffset >= sendList.size()) {
            sendOffset = 0;
        }
        sendList.get(sendOffset++).tell(msg, sender);
    }

    @Override
    public MsgType<T> msgType() {
        return msgType;
    }

    @Override
    public String toString() {
        return "LocalPubsub[" +
               "name=" + name + ", " +
               "msgType=" + msgType.name() +
               ']';
    }
}
