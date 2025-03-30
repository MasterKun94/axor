package io.axor.runtime;

import com.typesafe.config.Config;

public interface EventDispatcherGroupBuilderProvider<T extends EventDispatcherGroupBuilder<?>> extends Provider<T> {

    @Override
    default String group() {
        return "executor";
    }

    @Override
    T create(Config config);

    @Override
    default T createFromRootConfig(Config rootConfig) {
        return Provider.super.createFromRootConfig(rootConfig);
    }
}
