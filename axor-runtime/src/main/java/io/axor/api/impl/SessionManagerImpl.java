package io.axor.api.impl;

import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.SessionContext;
import io.axor.api.SessionDef;
import io.axor.api.SessionDefCreator;
import io.axor.api.SessionForceCloseSignal;
import io.axor.api.SessionManager;
import io.axor.commons.collection.LongObjectHashMap;
import io.axor.commons.collection.LongObjectMap;
import io.axor.runtime.Signal;
import org.agrona.collections.LongArrayList;

import java.util.ArrayList;
import java.util.List;

public class SessionManagerImpl<T> implements SessionManager<T> {
    public static final long NO_SESSION = -1;

    private final LongObjectMap<SessionDef<T>> sessions = new LongObjectHashMap<>();
    private final ActorContext<T> actorContext;
    private List<Long> toRemove = new LongArrayList();
    private long sessionId = 0;
    private int iterationState = 0;

    public SessionManagerImpl(ActorContext<T> actorContext) {
        this.actorContext = actorContext;
        ActorUnsafe.runOnStop(actorContext.self(), this::stop);
    }

    @Override
    public ActorContext<T> actorContext() {
        return actorContext;
    }

    @Override
    public long openSession(SessionDefCreator<T> creator) {
        long sessionId = ++this.sessionId;
        SessionContext<T> context = new SessionContext<>(sessionId, actorContext, this);
        SessionDef<T> sessionDef = creator.create(context);
        sessionDef.onStart();
        SessionDef<T> prev = sessions.putIfAbsent(sessionId, sessionDef);
        assert prev == null;
        return sessionId;
    }

    @Override
    public void closeSession(long sessionId) {
        if (iterationState > 0) {
            if (toRemove == null) {
                toRemove = new ArrayList<>();
            }
            toRemove.add(sessionId);
            return;
        }
        SessionDef<T> remove = sessions.remove(sessionId);
        if (remove != null) {
            remove.onStop();
        }
    }

    @Override
    public boolean sendMsgToAll(T msg, ActorRef<?> sender) {
        boolean send = false;
        try (var ignore = openScope()) {
            for (SessionDef<T> value : sessions.values()) {
                if (value.msgMatcher().matches(msg, sender)) {
                    value.onMsg(msg, sender);
                    send = true;
                }
            }
        }
        return send;
    }

    @Override
    public boolean sendSignalToAll(Signal signal) {
        boolean send = false;
        try (var ignore = openScope()) {
            for (SessionDef<T> value : sessions.values()) {
                if (value.signalMatcher().matches(signal)) {
                    value.onSignal(signal);
                    send = true;
                }
            }
        }
        return send;
    }

    @Override
    public boolean sendMsgToOne(T msg, ActorRef<?> sender) {
        try (var ignore = openScope()) {
            for (SessionDef<T> value : sessions.values()) {
                if (value.msgMatcher().matches(msg, sender)) {
                    value.onMsg(msg, sender);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean sendSignalToOne(Signal signal) {
        try (var ignore = openScope()) {
            for (SessionDef<T> value : sessions.values()) {
                if (value.signalMatcher().matches(signal)) {
                    value.onSignal(signal);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean publishMsgToAll(T msg, ActorRef<?> sender) {
        if (sessions.isEmpty()) {
            return false;
        }
        try (var ignore = openScope()) {
            sessions.values().forEach(def -> def.onMsg(msg, sender));
        }
        return true;
    }

    @Override
    public boolean publishSignalToAll(Signal signal) {
        if (sessions.isEmpty()) {
            return false;
        }
        try (var ignore = openScope()) {
            sessions.values().forEach(def -> def.onSignal(signal));
        }
        return true;
    }

    private void stop() {
        publishSignalToAll(new SessionForceCloseSignal());
        while (!sessions.isEmpty()) {
            SessionDef<T> remove = sessions.remove(sessions.keySet().iterator().next());
            if (remove != null) {
                remove.onStop();
            }
        }
    }

    private IterScope openScope() {
        return new IterScope();
    }

    private class IterScope implements AutoCloseable {

        IterScope() {
            iterationState++;
        }

        @Override
        public void close() {
            iterationState--;
            if (iterationState == 0 && toRemove != null) {
                while (!toRemove.isEmpty()) {
                    closeSession(toRemove.removeFirst());
                }
            }
        }
    }
}
