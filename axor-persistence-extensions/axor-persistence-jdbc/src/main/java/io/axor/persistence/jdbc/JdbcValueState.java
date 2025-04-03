package io.axor.persistence.jdbc;


import io.axor.persistence.state.ValueState;
import io.axor.persistence.state.ValueStateDef;

public class JdbcValueState<V, C> implements ValueState<V, C> {
    private final ValueStateDef<V, C> def;

    public JdbcValueState(ValueStateDef<V, C> def) {
        this.def = def;
    }

    @Override
    public V get() {
        return null;
    }

    @Override
    public void set(V value) {

    }

    @Override
    public V getAndSet(V value) {
        return null;
    }

    @Override
    public void remove() {

    }

    @Override
    public V getAndRemove() {
        return null;
    }

    @Override
    public void command(C command) {

    }

    @Override
    public V getAndCommand(C command) {
        return null;
    }

    @Override
    public V commandAndGet(C command) {
        return null;
    }
}
