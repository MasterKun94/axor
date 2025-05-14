package io.axor.flow;

import io.axor.api.ActorRef;
import io.axor.commons.concurrent.EventExecutor;

import java.util.function.Function;

public class DefaultFlow<T> implements Flow<T> {

    @Override
    public EventExecutor executor() {
        return null;
    }

    @Override
    public <P> Flow<P> map(Function<T, P> func) {
        return null;
    }

    @Override
    public Flow<T> limit(int limit) {
        return null;
    }

    @Override
    public void subscribe(ActorRef<FlowEvent<T>> subscriber) {

    }

    @Override
    public void subscribe(java.util.concurrent.Flow.Subscriber<? super T> subscriber) {

    }
}
