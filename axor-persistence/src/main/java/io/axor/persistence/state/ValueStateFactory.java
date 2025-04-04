package io.axor.persistence.state;

public interface ValueStateFactory {
    <V, C> ValueState<V, C> create(ValueStateDef<V, C> def);

    String impl();
}
