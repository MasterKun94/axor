package io.masterkun.axor.api.impl;

import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorRefRich;
import io.masterkun.axor.api.Signal;
import io.masterkun.axor.api.SystemEvent;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.StreamDefinition;
import io.masterkun.axor.runtime.StreamManager;

import java.util.List;

public class ForwardingActorRef<T> extends ActorRefRich<T> {
    private final ActorRefRich<T> delegate;

    public ForwardingActorRef(ActorRef<T> delegate) {
        this.delegate = (ActorRefRich<T>) delegate;
    }

    public ActorRef<T> getDelegate() {
        return delegate;
    }

    @Override
    public void addWatcher(ActorRef<?> watcher, List<Class<? extends SystemEvent>> watchEvents) {
        delegate.addWatcher(watcher, watchEvents);
    }

    @Override
    public void removeWatcher(ActorRef<?> watcher) {
        delegate.removeWatcher(watcher);
    }

    @Override
    public StreamDefinition<T> getDefinition() {
        return delegate.getDefinition();
    }

    @Override
    public StreamManager<T> getStreamManager() {
        return delegate.getStreamManager();
    }

    @Override
    protected void cleanup() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MsgType<? super T> msgType() {
        return delegate.msgType();
    }

    @Override
    public ActorAddress address() {
        return delegate.address();
    }

    @Override
    public void tell(T value, ActorRef<?> sender) {
        delegate.tell(value, sender);
    }

    @Override
    public boolean isLocal() {
        return delegate.isLocal();
    }

    @Override
    public boolean isNoSender() {
        return delegate.isNoSender();
    }

    public void signal(Signal signal) {
        if (delegate instanceof LocalActorRef<?> l) {
            l.signal(signal);
        } else {
            throw new UnsupportedOperationException("delegate not a LocalActorRef");
        }
    }
}
