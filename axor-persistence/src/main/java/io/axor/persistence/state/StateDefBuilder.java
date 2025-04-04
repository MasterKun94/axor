package io.axor.persistence.state;

import com.typesafe.config.Config;
import io.axor.runtime.Serde;

@SuppressWarnings("unchecked")
public class StateDefBuilder<V, B extends StateDefBuilder<?, ?>> {
    protected String name;
    protected String impl;
    protected ShareableScope scope;
    protected Config config;
    protected Serde<V> valueSerde;

    public B name(String name) {
        this.name = name;
        return (B) this;
    }

    public B impl(String impl) {
        this.impl = impl;
        return (B) this;
    }

    public B scope(ShareableScope scope) {
        this.scope = scope;
        return (B) this;
    }

    public B config(Config config) {
        this.config = config;
        return (B) this;
    }

    public <T> StateDefBuilder<T, ?> valueSerde(Serde<T> serde) {
        StateDefBuilder<T, B> builder = (StateDefBuilder<T, B>) this;
        builder.valueSerde = serde;
        return builder;
    }
}
