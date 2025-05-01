package io.axor.persistence.logging;

import io.axor.commons.concurrent.EventStage;

public interface PersistenceLoggingWriter<K, V> {
    EventStage<IdAndOffset> write(K key, V value);
}
