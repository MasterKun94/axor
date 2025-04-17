package io.axor.commons.stream;

import io.axor.commons.concurrent.EventStage;

public interface MapAsyncFunction<T, P> {
    EventStage<P> map(T data) throws Throwable;
}
