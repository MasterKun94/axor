package io.axor.persistence.state;

import com.typesafe.config.Config;

public sealed abstract class StateDef permits MapStateDef {
    private final String name;
    private final ShareableScope scope;
    private final Config config;

    protected StateDef(String name, ShareableScope scope, Config config) {
        this.name = name;
        this.scope = scope;
        this.config = config;
    }

    public String getName() {
        return name;
    }

    public ShareableScope getScope() {
        return scope;
    }

    public Config getConfig() {
        return config;
    }
}
