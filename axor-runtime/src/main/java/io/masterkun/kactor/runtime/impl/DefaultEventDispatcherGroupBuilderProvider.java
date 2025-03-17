package io.masterkun.kactor.runtime.impl;

import com.typesafe.config.Config;
import io.masterkun.kactor.runtime.EventDispatcherGroupBuilderProvider;

public class DefaultEventDispatcherGroupBuilderProvider implements EventDispatcherGroupBuilderProvider<DefaultEventDispatcherGroupBuilder> {
    @Override
    public int priority() {
        return 0;
    }

    @Override
    public String name() {
        return "default";
    }

    @Override
    public DefaultEventDispatcherGroupBuilder create() {
        return new DefaultEventDispatcherGroupBuilder();
    }

    @Override
    public DefaultEventDispatcherGroupBuilder create(Config config) {
        return new DefaultEventDispatcherGroupBuilder(config);
    }
}
