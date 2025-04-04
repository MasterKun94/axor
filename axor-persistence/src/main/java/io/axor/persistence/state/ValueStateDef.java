package io.axor.persistence.state;

import com.typesafe.config.Config;
import io.axor.runtime.Serde;

import java.util.Objects;

public sealed class ValueStateDef<V, C> extends StateDef<V> permits EventSourceValueStateDef {
    private final CommandFunction<V, C> commandFunction;

    protected ValueStateDef(String name, String impl, ShareableScope scope, Config config,
                            Serde<V> valueSerde, CommandFunction<V, C> commandFunction) {
        super(name, impl, scope, config, valueSerde);
        this.commandFunction = Objects.requireNonNull(commandFunction, "commandFunction");
    }

    public CommandFunction<V, C> getCommandFunction() {
        return commandFunction;
    }
}
