package io.axor.persistence.jdbc;

import io.axor.persistence.state.ValueState;
import io.axor.persistence.state.ValueStateDef;
import io.axor.persistence.state.ValueStateFactory;

public class JdbcValueStateFactory implements ValueStateFactory {
    @Override
    public <V, C> ValueState<V, C> create(ValueStateDef<V, C> def) {
        return new JdbcValueState<>(def);
    }

    @Override
    public String impl() {
        return "jdbc";
    }
}
