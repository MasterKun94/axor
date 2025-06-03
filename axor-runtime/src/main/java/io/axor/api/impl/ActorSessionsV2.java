package io.axor.api.impl;

import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.ActorSessions;
import io.axor.api.SessionContext;
import io.axor.api.SessionDef;
import io.axor.api.SessionManager;
import io.axor.api.SessionMsgMatcher;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;
import io.axor.runtime.MsgType;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

public class ActorSessionsV2<T> implements ActorSessions<T> {
    private final SessionManager<T> sessionManager;

    public ActorSessionsV2(ActorContext<T> context) {
        this.sessionManager = new SessionManagerImpl<>(context);
    }

    @Override
    public ActorContext<T> context() {
        return sessionManager.actorContext();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <P extends T> EventStage<P> expectReceive(ActorRef<?> from,
                                                     MsgType<? extends P> msgType,
                                                     Predicate<P> isMsg,
                                                     Duration timeout) {
        SessionMsgMatcher<T> msgMatcher = (msg, sender) ->
                (sender.address().equals(from.address()) &&
                 msgType.isSupport(msg.getClass()) &&
                 isMsg.test((P) msg));
        EventPromise<P> promise = sessionManager.actorContext().dispatcher().newPromise();
        long id = sessionManager.openSession(c -> new UnaryResSessionDef<>(c,
                msgMatcher, (EventPromise<T>) promise));
        sessionManager.actorContext().dispatcher().schedule(() -> {
            promise.failure(new TimeoutException());
            sessionManager.closeSession(id);
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        return promise;
    }

    boolean maybeNotify(T msg, ActorRef<?> sender) {
        return sessionManager.sendMsgToAll(msg, sender);
    }

    private static class UnaryResSessionDef<T> extends SessionDef<T> {
        private final SessionMsgMatcher<T> msgMatcher;
        private final EventPromise<T> promise;

        protected UnaryResSessionDef(SessionContext<T> sessionContext,
                                     SessionMsgMatcher<T> msgMatcher,
                                     EventPromise<T> promise) {
            super(sessionContext);
            this.msgMatcher = msgMatcher;
            this.promise = promise;
        }

        @Override
        public void onMsg(T msg, ActorRef<?> sender) {
            promise.success(msg);
            sessionContext().stop();
        }

        @Override
        public void onStop() {
            assert promise.isDone();
        }

        @Override
        public SessionMsgMatcher<T> msgMatcher() {
            return msgMatcher;
        }

    }
}
