package io.axor.api;

import io.axor.runtime.EventDispatcher;
import io.axor.runtime.MsgType;

public final class SessionContext<T> {
    private final long id;
    private final ActorContext<T> actorContext;
    private final SessionManager<T> sessionManager;

    public SessionContext(long id, ActorContext<T> actorContext, SessionManager<T> sessionManager) {
        this.id = id;
        this.actorContext = actorContext;
        this.sessionManager = sessionManager;
    }

    public long id() {
        return id;
    }

    public ActorContext<T> actorContext() {
        return actorContext;
    }

    public EventDispatcher dispatcher() {
        return actorContext.dispatcher();
    }

    public ActorRef<T> self() {
        return actorContext.self();
    }

    public ActorRef<?> sender() {
        return actorContext.sender();
    }

    public <P> ActorRef<P> sender(Class<P> checkedType) {
        return sender(MsgType.of(checkedType));
    }

    public <P> ActorRef<P> sender(MsgType<P> checkedType) {
        return sender().cast(checkedType);
    }

    public void stop() {
        sessionManager.closeSession(id);
    }

    @Override
    public String toString() {
        return "SessionContext[" + actorContext.self().address() + "#" + id + "]";
    }

}
