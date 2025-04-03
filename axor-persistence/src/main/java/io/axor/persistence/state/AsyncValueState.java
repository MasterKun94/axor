package io.axor.persistence.state;

import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;

public interface AsyncValueState<V, C> {
    EventStage<V> get(EventPromise<V> promise);

    EventStage<Void> set(V value, EventPromise<Void> promise);

    EventStage<V> getAndSet(V value, EventPromise<V> promise);

    EventStage<Void> remove(EventPromise<Void> promise);

    EventStage<V> getAndRemove(EventPromise<V> promise);

    EventStage<Void> command(C command, EventPromise<Void> promise);

    EventStage<V> getAndCommand(C command, EventPromise<V> promise);

    EventStage<V> commandAndGet(C command, EventPromise<V> promise);
}
