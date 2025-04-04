package io.axor.persistence.jdbc;

import io.axor.persistence.state.MapState;
import io.axor.persistence.state.MapStateDef;
import io.axor.persistence.state.MapStateFactory;

public class JdbcMapStateFactory implements MapStateFactory {
    @Override
    public <K, V, C> MapState<K, V, C> create(MapStateDef<K, V, C> def) {
        return new JdbcMapState<>(def);
    }

    @Override
    public String impl() {
        return "jdbc";
    }
}
