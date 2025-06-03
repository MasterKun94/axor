package io.axor.api;

import io.axor.runtime.Signal;

public abstract class SessionDef<T> {
    private final SessionContext<T> sessionContext;

    protected SessionDef(SessionContext<T> sessionContext) {
        this.sessionContext = sessionContext;
    }

    protected SessionContext<T> sessionContext() {
        return sessionContext;
    }

    public void onStart() {
    }

    public void onStop() {
    }

    public abstract void onMsg(T msg, ActorRef<?> sender);

    public void onSignal(Signal signal) {
    }

    public abstract SessionMsgMatcher<T> msgMatcher();

    public SessionSignalMatcher signalMatcher() {
        return signal -> false;
    }
}
