package io.masterkun.axor.api.impl;

import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.Pubsub;
import io.masterkun.axor.runtime.EventDispatcher;
import io.masterkun.axor.runtime.MsgType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A local implementation of the {@link Pubsub} interface, designed to handle message distribution within a single JVM.
 * This class manages a list of subscribed actors and provides methods to publish messages to all subscribers or to a
 * single subscriber. The class ensures thread safety by using an {@link EventDispatcher} to manage concurrent access.
 *
 * @param <T> the type of the message that can be published and received through this pubsub mechanism
 */
public class LocalPubsub<T> implements Pubsub<T> {
    private static final Logger LOG = LoggerFactory.getLogger(LocalPubsub.class);

    private final MsgType<T> msgType;
    private final Map<ActorAddress, ActorRef<? super T>> actors = new HashMap<>();
    private final List<ActorRef<? super T>> sendList = new ArrayList<>();
    private final EventDispatcher executor;
    private final boolean logUnSendMsg;
    private int sendOffset = 0;

    public LocalPubsub(MsgType<T> msgType, EventDispatcher executor, boolean logUnSendMsg) {
        this.msgType = msgType;
        this.executor = executor;
        this.logUnSendMsg = logUnSendMsg;
    }

    public LocalPubsub(MsgType<T> msgType, EventDispatcher executor) {
        this(msgType, executor, true);
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
        if (!executor.inExecutor()) {
            executor.execute(() -> publishToAll(msg, sender));
            return;
        }
        if (actors.isEmpty()) {
            if (logUnSendMsg) {
                LOG.warn("{} has no actors to publish for this message", this);
            }
            return;
        }
        for (ActorRef<? super T> actorRef : sendList) {
            if (actorRef instanceof LocalActorRef<? super T> l && l.isStopped()) {
                sendList.remove(actorRef);
                LOG.warn("{} already stopped", actorRef);
                continue;
            }
            actorRef.tell(msg, sender);
        }
    }

    @Override
    public void sendToOne(T msg, ActorRef<?> sender) {
        if (!executor.inExecutor()) {
            executor.execute(() -> sendToOne(msg, sender));
            return;
        }
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
                "msgType=" + msgType +
                ']';
    }
}
