package io.masterkun.axor.runtime.impl;

import com.typesafe.config.Config;
import io.masterkun.axor.runtime.EventDispatcherGroupBuilder;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultEventDispatcherGroupBuilder implements EventDispatcherGroupBuilder<DefaultEventDispatcherGroupBuilder> {
    private static final AtomicInteger GROUP_ID_ADDER = new AtomicInteger();
    private String name = "DefaultEventDispatcher-%s-%s";
    private int threads = Runtime.getRuntime().availableProcessors();

    public DefaultEventDispatcherGroupBuilder() {
    }

    public DefaultEventDispatcherGroupBuilder(Config config) {
        executorName(config.getString("executorName"));
        threads(config.getInt("threads"));
    }

    @Override
    public DefaultEventDispatcherGroupBuilder executorName(String name) {
        this.name = Objects.requireNonNull(name);
        return this;
    }

    @Override
    public DefaultEventDispatcherGroupBuilder threads(int threads) {
        if (threads == -1) {
            this.threads = Runtime.getRuntime().availableProcessors();
        } else if (threads > 0) {
            this.threads = threads;
        } else {
            throw new IllegalArgumentException("threads must be greater than zero");
        }
        return this;
    }

    @Override
    public DefaultEventDispatcherGroup build() {
        return new DefaultEventDispatcherGroup(name, threads, GROUP_ID_ADDER.getAndIncrement());
    }
}
