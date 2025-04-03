package io.axor.persistence.state;

public interface ValueState<V, C> {
    V get();

    void set(V value);

    V getAndSet(V value);

    void remove();

    V getAndRemove();

    void command(C command);

    V getAndCommand(C command);

    V commandAndGet(C command);
}
