package io.axor.api.impl;

import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.ActorRefRich;
import io.axor.api.SystemEvent;
import io.axor.runtime.MsgType;
import io.axor.runtime.Signal;
import io.axor.runtime.StreamDefinition;
import io.axor.runtime.StreamManager;

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
    public void tell(T value) {
        delegate.tell(value);
    }

    @Override
    public void tellInline(T value, ActorRef<?> sender) {
        delegate.tellInline(value, sender);
    }

    @Override
    public boolean isLocal() {
        return delegate.isLocal();
    }

    @Override
    public boolean isNoSender() {
        return delegate.isNoSender();
    }

    @Override
    public void signal(Signal signal) {
        delegate.signal(signal);
    }

    public void signal(Signal signal, ActorRef<?> ref) {
        if (delegate instanceof RemoteActorRef<T> f) {
            f.signal(signal, ref);
        } else {
            signal(signal);
        }
    }

    @Override
    public void signalInline(Signal signal) {
        delegate.signalInline(signal);
    }
}
