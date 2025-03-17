package io.masterkun.kactor.runtime.impl;

import io.masterkun.kactor.runtime.EventDispatcher;
import io.masterkun.kactor.runtime.StreamDefinition;
import io.masterkun.kactor.runtime.StreamOutChannel;

public class NoopStreamOutChannel<T> implements StreamOutChannel<T> {
    private final StreamDefinition<T> selfDefinition;

    public NoopStreamOutChannel(StreamDefinition<T> selfDefinition) {
        this.selfDefinition = selfDefinition;
    }

    @Override
    public <OUT> StreamObserver<OUT> open(StreamDefinition<OUT> to, EventDispatcher executor, Observer observer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StreamDefinition<T> getSelfDefinition() {
        return selfDefinition;
    }
}
