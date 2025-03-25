package io.masterkun.axor.testkit.actor;

import io.masterkun.axor.api.Actor;
import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorContext;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.api.Signal;
import io.masterkun.axor.api.impl.ActorUnsafe;
import io.masterkun.axor.runtime.MsgType;

import java.time.Duration;

public class ActorTestKit {
    private long timeTimeMills;

    public ActorTestKit() {
    }

    public ActorTestKit(Duration timeout) {
        setMsgTimeout(timeout);
    }

    public void setMsgTimeout(Duration timeout) {
        timeTimeMills = timeout.toMillis();
    }

    public <T> MockActorRef<T> mock(ActorAddress address, MsgType<T> msgType) {
        return mock(new NoopActorRef<>(address, msgType));
    }

    public <T> MockActorRef<T> mock(ActorAddress address, Class<T> msgType) {
        return mock(new NoopActorRef<>(address, MsgType.of(msgType)));
    }

    public <T> MockActorRef<T> mock(ActorRef<T> ref) {
        return new MockActorRef<>(ref, timeTimeMills);
    }

    public <T> MockActorRef<T> mock(String name, MsgType<T> msgType, ActorSystem system) {
        MockActorRef<T> mock = mock(system.address(name), msgType);
        ActorRef<T> start = system.start(c -> new MockActor<>(c, msgType, mock), name);
        mock.combineWith(start);
        ActorUnsafe.replaceCache(system, mock);
        return mock;
    }

    public <T> MockActorRef<T> mock(String name, Class<T> msgType, ActorSystem system) {
        return mock(name, MsgType.of(msgType), system);
    }

    private static class MockActor<T> extends Actor<T> {
        private final MsgType<T> msgType;
        private final MockActorRef<T> mock;

        protected MockActor(ActorContext<T> context, MsgType<T> msgType, MockActorRef<T> mock) {
            super(context);
            this.msgType = msgType;
            this.mock = mock;
        }

        @Override
        public void onReceive(T t) {
            mock.tell(t, sender());
        }

        @Override
        public void onSignal(Signal signal) {
            mock.signal(signal);
        }

        @Override
        public MsgType<T> msgType() {
            return msgType;
        }
    }
}
