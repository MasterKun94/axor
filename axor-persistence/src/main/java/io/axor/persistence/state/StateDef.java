package io.axor.persistence.state;

import com.typesafe.config.Config;
import io.axor.runtime.Serde;

import java.util.Objects;

public sealed abstract class StateDef<V> permits ValueStateDef, MapStateDef {
    private final String name;
    private final String impl;
    private final ShareableScope scope;
    private final Config config;
    private final Serde<V> valueSerde;

    protected StateDef(String name, String impl, ShareableScope scope, Config config,
                       Serde<V> valueSerde) {
        this.name = Objects.requireNonNull(name, "name");
        this.impl = Objects.requireNonNull(impl, "impl");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.config = Objects.requireNonNull(config, "config");
        this.valueSerde = Objects.requireNonNull(valueSerde, "valueSerde");
    }

    public String getName() {
        return name;
    }

    public String getImpl() {
        return impl;
    }

    public ShareableScope getScope() {
        return scope;
    }

    public Config getConfig() {
        return config;
    }

    public Serde<V> getValueSerde() {
        return valueSerde;
    }
}
