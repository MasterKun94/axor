package io.axor.commons.concurrent;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

public class MeterEventExecutor extends ForwardingEventExecutor {
    private final SingleThreadEventExecutor delegate;
    private final ScheduledExecutorService meterExecutor;

    public MeterEventExecutor(EventExecutor executor, MeterRegistry registry,
                              String metricPrefix, List<Tag> tagList) {
        this.delegate = (SingleThreadEventExecutor) executor;
        this.meterExecutor = ExecutorServiceMetrics.monitor(registry, executor,
                delegate.threadName(), metricPrefix, tagList);
    }

    @Override
    protected SingleThreadEventExecutor delegate() {
        return delegate;
    }

    @Override
    protected ScheduledExecutorService scheduledDelegate() {
        return meterExecutor;
    }
}
