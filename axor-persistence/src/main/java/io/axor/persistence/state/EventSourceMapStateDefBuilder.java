package io.axor.persistence.state;

import io.axor.runtime.Serde;

public class EventSourceMapStateDefBuilder<K, V, C> extends StateDefBuilder<V,
        EventSourceMapStateDefBuilder<K, V, C>> {
    private Serde<K> keySerde;
    private Serde<C> commandSerde;

    public <T> EventSourceMapStateDefBuilder<T, V, C> keySerde(Serde<T> keySerde) {
        @SuppressWarnings("unchecked")
        var cast = (EventSourceMapStateDefBuilder<T, V, C>) this;
        cast.keySerde = keySerde;
        return cast;
    }

    public <E> EventSourceMapStateDefBuilder<K, V, E> commandSerde(Serde<E> commandSerde) {
        @SuppressWarnings("unchecked")
        var cast = (EventSourceMapStateDefBuilder<K, V, E>) this;
        cast.commandSerde = commandSerde;
        return cast;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> EventSourceMapStateDefBuilder<K, T, C> valueSerde(Serde<T> serde) {
        return (EventSourceMapStateDefBuilder<K, T, C>) super.valueSerde(serde);
    }

    public EventSourceMapStateDef<K, V, C> build(KeyedCommandFunction<K, V, C> commandFunction) {
        return new EventSourceMapStateDef<>(name, impl, scope, config, keySerde, valueSerde,
                commandFunction, commandSerde);
    }
}
