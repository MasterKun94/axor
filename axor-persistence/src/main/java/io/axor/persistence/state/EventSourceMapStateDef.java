package io.axor.persistence.state;

import com.typesafe.config.Config;
import io.axor.runtime.Serde;

public final class EventSourceMapStateDef<K, V, C> extends MapStateDef<K, V, C> {
    private final Serde<C> commandSerde;

    EventSourceMapStateDef(String name, String impl, ShareableScope scope, Config config,
                           Serde<K> keySerde, Serde<V> valueSerde,
                           KeyedCommandFunction<K, V, C> commandFunction, Serde<C> commandSerde) {
        super(name, impl, scope, config, keySerde, valueSerde, commandFunction);
        this.commandSerde = commandSerde;
    }

    public Serde<C> getCommandSerde() {
        return commandSerde;
    }
}
