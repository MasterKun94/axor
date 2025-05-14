package io.axor.raft;

import io.axor.api.Actor;
import io.axor.api.ActorContext;
import io.axor.api.FailureStrategy;
import io.axor.runtime.MsgType;

public class StatemachineActor extends Actor<StatemachineEvent> {

    protected StatemachineActor(ActorContext<StatemachineEvent> context) {
        super(context);
    }

    @Override
    public void onReceive(StatemachineEvent statemachineMessage) {

    }

    @Override
    public FailureStrategy failureStrategy(Throwable throwable) {
        return FailureStrategy.SYSTEM_ERROR;
    }

    @Override
    public MsgType<StatemachineEvent> msgType() {
        return MsgType.of(StatemachineEvent.class);
    }
}
