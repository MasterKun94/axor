package io.masterkun.axor.testkit.actor;

import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorRefRich;
import io.masterkun.axor.api.SystemEvent;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.StreamDefinition;
import io.masterkun.axor.runtime.StreamManager;

import java.util.List;

public class NoopActorRef<T> extends ActorRefRich<T> {

    private final ActorAddress address;
    private final MsgType<T> msgType;

    public NoopActorRef(ActorAddress address, MsgType<T> msgType) {
        this.address = address;
        this.msgType = msgType;
    }

    @Override
    public MsgType<? super T> msgType() {
        return msgType;
    }

    @Override
    public ActorAddress address() {
        return address;
    }

    @Override
    public void tell(T value, ActorRef<?> sender) {
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public void addWatcher(ActorRef<?> watcher, List<Class<? extends SystemEvent>> watchEvents) {

    }

    @Override
    public void removeWatcher(ActorRef<?> watcher) {

    }

    @Override
    public StreamDefinition<T> getDefinition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public StreamManager<T> getStreamManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void cleanup() {

    }
}
