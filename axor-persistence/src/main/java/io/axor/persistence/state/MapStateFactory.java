package io.axor.persistence.state;

public interface MapStateFactory {
    <K, V, C> MapState<K, V, C> create(MapStateDef<K, V, C> def);

    String impl();
}
