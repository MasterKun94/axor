package io.axor.api;

import io.axor.commons.concurrent.EventStage;
import io.axor.runtime.MsgType;

import java.time.Duration;
import java.util.function.Predicate;

public interface ActorSessions<T> {
    ActorContext<T> context();

    <P extends T> EventStage<P> expectReceive(ActorRef<?> from, MsgType<? extends P> msgType,
                                              Predicate<P> isMsg, Duration timeout);

    default ReceiveFrom<T, T> expectReceiveFrom(ActorRef<?> from) {
        @SuppressWarnings("unchecked")
        MsgType<T> msgType = (MsgType<T>) context().self().msgType();
        return new ReceiveFrom<>(this, from, msgType);
    }

    class ReceiveFrom<T, P extends T> {
        private final ActorSessions<T> sessions;
        private final ActorRef<?> from;
        private final MsgType<P> msgType;

        private ReceiveFrom(ActorSessions<T> sessions, ActorRef<?> from, MsgType<P> msgType) {
            this.sessions = sessions;
            this.from = from;
            this.msgType = msgType;
        }

        public <Q extends T> ReceiveFrom<T, Q> msgType(MsgType<Q> msgType) {
            return new ReceiveFrom<>(sessions, from, msgType);
        }


        public <Q extends T> ReceiveFrom<T, Q> msgType(Class<Q> msgType) {
            return msgType(MsgType.of(msgType));
        }

        public ReceiveFromP2<T, P> msgPredicate(Predicate<P> msgPredicate) {
            return new ReceiveFromP2<>(sessions, from, msgType, msgPredicate);
        }

        public EventStage<P> listen(Duration timeout) {
            return sessions.expectReceive(from, msgType, t -> true, timeout);
        }
    }

    class ReceiveFromP2<T, P extends T> {
        private final ActorSessions<T> sessions;
        private final ActorRef<?> from;
        private final MsgType<? extends P> msgType;
        private final Predicate<P> msgPredicate;

        private ReceiveFromP2(ActorSessions<T> sessions, ActorRef<?> from, MsgType<P> msgType,
                              Predicate<P> msgPredicate) {
            this.sessions = sessions;
            this.from = from;
            this.msgType = msgType;
            this.msgPredicate = msgPredicate;
        }

        public EventStage<P> listen(Duration timeout) {
            return sessions.expectReceive(from, msgType, msgPredicate, timeout);
        }
    }
}
