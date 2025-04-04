package io.axor.persistence.state;

public interface EventSourceValueStateFactory {
    <V, C> ValueState<V, C> create(EventSourceValueStateDef<V, C> def);

    String impl();

}
