package io.axor.commons.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class ForwardingEventExecutor implements SingleThreadEventExecutor {

    protected abstract SingleThreadEventExecutor delegate();

    protected ScheduledExecutorService scheduledDelegate() {
        return delegate();
    }

    @Override
    public String threadName() {
        return delegate().threadName();
    }

    @Override
    public boolean inExecutor() {
        return delegate().inExecutor();
    }

    @Override
    public <T> CompletableFuture<T> timeout(CompletableFuture<T> future, long timeout,
                                            TimeUnit unit) {
        return delegate().timeout(future, timeout, unit);
    }

    @Override
    public <T> EventPromise<T> timeout(EventPromise<T> promise, long timeout, TimeUnit unit) {
        return delegate().timeout(promise, timeout, unit);
    }

    @Override
    public <T> EventPromise<T> newPromise() {
        return delegate().newPromise();
    }

    @Override
    public <T> EventPromise<T> newFuturePromise() {
        return delegate().newFuturePromise();
    }

    @Override
    public CompletableFuture<Void> shutdownAsync() {
        return delegate().shutdownAsync();
    }

    @NotNull
    @Override
    public ScheduledFuture<?> schedule(@NotNull Runnable command, long delay,
                                       @NotNull TimeUnit unit) {
        return scheduledDelegate().schedule(command, delay, unit);
    }

    @NotNull
    @Override
    public <V> ScheduledFuture<V> schedule(@NotNull Callable<V> callable, long delay,
                                           @NotNull TimeUnit unit) {
        return scheduledDelegate().schedule(callable, delay, unit);
    }

    @NotNull
    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(@NotNull Runnable command, long initialDelay,
                                                  long period, @NotNull TimeUnit unit) {
        return scheduledDelegate().scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @NotNull
    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(@NotNull Runnable command, long initialDelay
            , long delay, @NotNull TimeUnit unit) {
        return scheduledDelegate().scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    // 以下是从 ExecutorService 继承的方法
    @Override
    public void shutdown() {
        scheduledDelegate().shutdown();
    }

    @NotNull
    @Override
    public List<Runnable> shutdownNow() {
        return scheduledDelegate().shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return scheduledDelegate().isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return scheduledDelegate().isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        return scheduledDelegate().awaitTermination(timeout, unit);
    }

    @NotNull
    @Override
    public <T> Future<T> submit(@NotNull Callable<T> task) {
        return scheduledDelegate().submit(task);
    }

    @NotNull
    @Override
    public <T> Future<T> submit(@NotNull Runnable task, T result) {
        return scheduledDelegate().submit(task, result);
    }

    @NotNull
    @Override
    public Future<?> submit(@NotNull Runnable task) {
        return scheduledDelegate().submit(task);
    }

    @NotNull
    @Override
    public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return scheduledDelegate().invokeAll(tasks);
    }

    @NotNull
    @Override
    public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks,
                                         long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        return scheduledDelegate().invokeAll(tasks, timeout, unit);
    }

    @NotNull
    @Override
    public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return scheduledDelegate().invokeAny(tasks);
    }

    @NotNull
    @Override
    public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks, long timeout,
                           @NotNull TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        return scheduledDelegate().invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(@NotNull Runnable command) {
        scheduledDelegate().execute(command);
    }
}
