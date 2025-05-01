package io.axor.persistence.logging;

import io.axor.commons.concurrent.EventStage;

public interface PersistenceLogging<T> {
    EventStage<IdAndOffset> write(T record);
}
