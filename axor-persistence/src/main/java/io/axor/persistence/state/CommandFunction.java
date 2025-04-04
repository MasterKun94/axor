package io.axor.persistence.state;

public interface CommandFunction<V, C> {
    V apply(V value, C command);
}
