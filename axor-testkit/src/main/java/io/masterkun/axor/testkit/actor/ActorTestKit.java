package io.masterkun.axor.testkit.actor;

import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorRef;
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
}
