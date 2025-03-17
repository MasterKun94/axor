package io.masterkun.axor.runtime.impl;

import io.masterkun.axor.runtime.EventDispatcher;
import io.masterkun.axor.runtime.EventDispatcherGroup;
import io.masterkun.axor.runtime.HasMeter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

public class AbstractEventDispatcherGroup implements EventDispatcherGroup, HasMeter {
    private final AtomicInteger ADDER = new AtomicInteger();
    private final List<EventDispatcher> executors;

    public AbstractEventDispatcherGroup(int threads, IntFunction<EventDispatcher> executorCreator) {
        this.executors = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            executors.add(executorCreator.apply(i));
        }
    }

    @Override
    public EventDispatcher nextExecutor() {
        return executors.get(ADDER.getAndIncrement() % executors.size());
    }

    @Override
    public Iterator<EventDispatcher> iterator() {
        return executors.iterator();
    }

    @Override
    public void register(MeterRegistry registry, String... tags) {
        String[] newTags = new String[tags.length + 2];
        System.arraycopy(tags, 0, newTags, 0, tags.length);
        newTags[newTags.length - 2] = "id";
        for (int i = 0; i < executors.size(); i++) {
            EventDispatcher executor = executors.get(i);
            if (executor instanceof HasMeter hasMetrics) {
                newTags[newTags.length - 1] = Integer.toString(i);
                hasMetrics.register(registry, newTags);
            }
        }
    }
}
