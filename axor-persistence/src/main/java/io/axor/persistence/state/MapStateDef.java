package io.axor.persistence.state;

import com.typesafe.config.Config;
import io.axor.runtime.Serde;

public sealed class MapStateDef<K, V, C> extends StateDef<V> permits EventSourceMapStateDef {
    private final Serde<K> keySerde;
    private final KeyedCommandFunction<K, V, C> commandFunction;

    protected MapStateDef(String name, String impl, ShareableScope scope, Config config,
                          Serde<K> keySerde, Serde<V> valueSerde,
                          KeyedCommandFunction<K, V, C> commandFunction) {
        super(name, impl, scope, config, valueSerde);
        this.keySerde = keySerde;
        this.commandFunction = commandFunction;
    }

    public Serde<K> getKeySerde() {
        return keySerde;
    }

    public KeyedCommandFunction<K, V, C> getCommandFunction() {
        return commandFunction;
    }
}
