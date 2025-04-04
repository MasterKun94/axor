package io.axor.persistence.state;

public interface EventSourceMapStateFactory {
    <K, V, C> MapState<K, V, C> create(EventSourceMapStateDef<K, V, C> def);

    String impl();

}
