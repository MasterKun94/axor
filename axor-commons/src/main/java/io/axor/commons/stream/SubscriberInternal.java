package io.axor.commons.stream;


import io.axor.commons.stream.EventFlow.Subscriber;

class SubscriberInternal<T> implements Subscriber<T> {

    private final Subscriber<T> delegate;

    SubscriberInternal(Subscriber<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onEvent(T event) {
        if (delegate.continueFlag()) {
            delegate.onEvent(event);
        }
    }

    @Override
    public void onSignal(EventFlow.Signal signal) {
        if (delegate.continueFlag()) {
            delegate.onSignal(signal);
        }
    }

    @Override
    public void onEnd() {
        delegate.onEnd();
    }

    @Override
    public boolean continueFlag() {
        return delegate.continueFlag();
    }
}
