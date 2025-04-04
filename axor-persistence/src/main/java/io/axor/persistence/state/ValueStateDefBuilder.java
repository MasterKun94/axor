package io.axor.persistence.state;

import io.axor.runtime.Serde;

public class ValueStateDefBuilder<V> extends StateDefBuilder<V, ValueStateDefBuilder<V>> {

    @SuppressWarnings("unchecked")
    @Override
    public <T> ValueStateDefBuilder<T> valueSerde(Serde<T> serde) {
        return (ValueStateDefBuilder<T>) super.valueSerde(serde);
    }

    public <C> ValueStateDef<V, C> build(CommandFunction<V, C> commandFunction) {
        return new ValueStateDef<>(name, impl, scope, config, valueSerde, commandFunction);
    }
}
