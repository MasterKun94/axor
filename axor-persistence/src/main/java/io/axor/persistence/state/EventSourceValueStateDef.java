package io.axor.persistence.state;

import com.typesafe.config.Config;
import io.axor.runtime.Serde;

import java.util.Objects;

public final class EventSourceValueStateDef<V, C> extends ValueStateDef<V, C> {
    private final Serde<C> commandSerde;

    EventSourceValueStateDef(String name, String impl, ShareableScope scope, Config config,
                             Serde<V> valueSerde, CommandFunction<V, C> commandFunction,
                             Serde<C> commandSerde) {
        super(name, impl, scope, config, valueSerde, commandFunction);
        this.commandSerde = Objects.requireNonNull(commandSerde, "commandSerde");
    }

    public Serde<C> getCommandSerde() {
        return commandSerde;
    }
}
