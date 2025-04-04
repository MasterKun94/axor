package io.axor.persistence.state;

import io.axor.runtime.Serde;

public class MapStateDefBuilder<K, V> extends StateDefBuilder<V, MapStateDefBuilder<K, V>> {
    private Serde<K> keySerde;

    public <T> MapStateDefBuilder<T, V> keySerde(Serde<T> keySerde) {
        @SuppressWarnings("unchecked")
        var cast = (MapStateDefBuilder<T, V>) this;
        cast.keySerde = keySerde;
        return cast;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> MapStateDefBuilder<K, T> valueSerde(Serde<T> serde) {
        return (MapStateDefBuilder<K, T>) super.valueSerde(serde);
    }

    public <C> MapStateDef<K, V, C> build(KeyedCommandFunction<K, V, C> commandFunction) {
        return new MapStateDef<>(name, impl, scope, config, keySerde, valueSerde, commandFunction);
    }
}
