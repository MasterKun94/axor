package io.axor.flow;

import io.axor.api.ActorRef;
import io.axor.commons.concurrent.EventExecutor;

import java.util.concurrent.Flow.Publisher;
import java.util.function.Function;

public interface Flow<T> extends Publisher<T> {
    EventExecutor executor();

    <P> Flow<P> map(Function<T, P> func);

    Flow<T> limit(int limit);

    void subscribe(ActorRef<FlowEvent<T>> subscriber);
}
