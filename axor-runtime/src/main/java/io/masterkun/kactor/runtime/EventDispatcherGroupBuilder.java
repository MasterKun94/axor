package io.masterkun.kactor.runtime;

public interface EventDispatcherGroupBuilder<T extends EventDispatcherGroupBuilder<T>> {
    static EventDispatcherGroupBuilder<?> create(String name) {
        return Registry.createByName(EventDispatcherGroupBuilderProvider.class, name);
    }

    static EventDispatcherGroupBuilder<?> create() {
        return Registry.createByPriority(EventDispatcherGroupBuilderProvider.class);
    }

    T executorName(String name);

    T threads(int threads);

    EventDispatcherGroup build();
}
