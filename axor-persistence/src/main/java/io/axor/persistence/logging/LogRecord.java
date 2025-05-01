package io.axor.persistence.logging;

import org.jetbrains.annotations.Nullable;

public record LogRecord<K, V>(long id, long offset, @Nullable K key, V value) {
}
