package io.axor.persistence.logging;

import io.axor.commons.stream.EventStream;

public interface PersistenceLoggingReader<K, V> {
    default EventStream<LogRecord<K, V>> read(long startFromId) {
        return read(-1, startFromId);
    }

    EventStream<LogRecord<K, V>> read(long startFromOffset, long startFromId);
}
