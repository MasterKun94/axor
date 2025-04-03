package io.axor.persistence.state;

import com.typesafe.config.Config;
import org.jetbrains.annotations.Nullable;

public abstract class ValueStateDef<V, C> {
    private final String name;
    private final ShareableScope scope;
    private final Config config;

    protected ValueStateDef(String name, ShareableScope scope, Config config) {
        this.name = name;
        this.scope = scope;
        this.config = config;
    }

    @Nullable
    public abstract V command(V prev, C command);
}
