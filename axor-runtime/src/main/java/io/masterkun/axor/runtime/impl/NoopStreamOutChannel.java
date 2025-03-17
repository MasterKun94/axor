package io.masterkun.axor.runtime.impl;

import io.masterkun.axor.runtime.EventDispatcher;
import io.masterkun.axor.runtime.StreamDefinition;
import io.masterkun.axor.runtime.StreamOutChannel;

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
