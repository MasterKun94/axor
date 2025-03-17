package io.masterkun.kactor.runtime.impl;

import io.masterkun.kactor.runtime.EventDispatcher;
import io.masterkun.kactor.runtime.scheduler.Scheduler;

import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class EventDispatcherWithScheduler extends AbstractExecutorService implements EventDispatcher {
    private final Scheduler scheduler;

    public EventDispatcherWithScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return scheduler.schedule(command, delay, unit, this);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return scheduler.schedule(callable, delay, unit, this);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return scheduler.scheduleAtFixedRate(command, initialDelay, period, unit, this);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return scheduler.scheduleWithFixedDelay(command, initialDelay, delay, unit, this);
    }

    @Override
    public <T> CompletableFuture<T> timeout(CompletableFuture<T> future, long timeout, TimeUnit unit) {
        return scheduler.setTimeout(future, timeout, unit);
    }
}
