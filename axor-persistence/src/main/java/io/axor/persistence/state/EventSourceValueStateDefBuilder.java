package io.axor.persistence.state;

import io.axor.runtime.Serde;

public class EventSourceValueStateDefBuilder<V, C>
        extends StateDefBuilder<V, EventSourceValueStateDefBuilder<V, C>> {
    private Serde<C> commandSerde;

    public <E> EventSourceValueStateDefBuilder<V, E> commandSerde(Serde<E> commandSerde) {
        @SuppressWarnings("unchecked")
        var cast = (EventSourceValueStateDefBuilder<V, E>) this;
        cast.commandSerde = commandSerde;
        return cast;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> EventSourceValueStateDefBuilder<T, C> valueSerde(Serde<T> serde) {
        return (EventSourceValueStateDefBuilder<T, C>) super.valueSerde(serde);
    }

    public EventSourceValueStateDef<V, C> build(CommandFunction<V, C> commandFunction) {
        return new EventSourceValueStateDef<>(name, impl, scope, config, valueSerde,
                commandFunction, commandSerde);
    }
}
