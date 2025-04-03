package io.axor.commons.concurrent;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscUnboundedArrayQueue;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A single-threaded event executor that handles tasks and scheduled tasks in a first-in-first-out
 * manner. This class extends {@link AbstractExecutorService} and implements the
 * {@link SingleThreadEventExecutor} interface, providing a way to schedule tasks for execution
 * after a delay or at fixed intervals.
 *
 * <p>The executor creates a single background thread to process all submitted tasks. It supports
 * scheduling both one-time and periodic tasks, with options for fixed-rate and fixed-delay
 * scheduling.
 *
 * @see AbstractExecutorService
 * @see SingleThreadEventExecutor
 */
public class DefaultSingleThreadEventExecutor extends AbstractExecutorService
        implements SingleThreadEventExecutor {
    private static final Logger LOG =
            LoggerFactory.getLogger(DefaultSingleThreadEventExecutor.class);
    private static final AtomicInteger ADDER = new AtomicInteger();
    private static final long NANO_ORIGIN = System.nanoTime();
    private final Thread thread;
    private final PriorityQueue<TimeoutTask<?>> timeoutTasks = new PriorityQueue<>();
    private final MessagePassingQueue<Runnable> taskQueue = new MpscUnboundedArrayQueue<>(1024);
    private boolean running = true;
    private volatile Thread idleThread;
    private volatile CompletableFuture<Void> terminatedFuture;

    public DefaultSingleThreadEventExecutor() {
        this(null);
    }

    public DefaultSingleThreadEventExecutor(@Nullable EventExecutorThreadFactory threadFactory) {
        if (threadFactory == null) {
            threadFactory = (executor, runnable) -> {
                EventExecutorThread t = new EventExecutorThread(executor, runnable);
                t.setName("SingleThreadEventExecutor-" + ADDER.getAndIncrement());
                t.setDaemon(false);
                return t;
            };
        }
        this.thread = threadFactory.newThread(this, new WorkerRunner());
        this.thread.start();
    }

    public boolean inExecutor() {
        return Thread.currentThread() == this.thread;
    }

    private long now() {
        return System.nanoTime() - NANO_ORIGIN;
    }

    @Override
    public String threadName() {
        return thread.getName();
    }

    @Internal
    public Thread getThread() {
        return thread;
    }

    @Override
    public @NotNull ScheduledFuture<?> schedule(@NotNull Runnable command, long delay,
                                                @NotNull TimeUnit unit) {
        FutureTask<?> task = new FutureTask<>(command, null);
        return new ScheduleOnce<>(this, task, delay, unit);
    }

    @Override
    public @NotNull <V> ScheduledFuture<V> schedule(@NotNull Callable<V> callable, long delay,
                                                    @NotNull TimeUnit unit) {
        FutureTask<V> task = new FutureTask<>(callable);
        return new ScheduleOnce<>(this, task, delay, unit);
    }

    @Override
    public @NotNull ScheduledFuture<?> scheduleAtFixedRate(@NotNull Runnable command,
                                                           long initialDelay, long period,
                                                           @NotNull TimeUnit unit) {
        return new ScheduleFixRate(this, command, initialDelay, period, unit);
    }

    @Override
    public @NotNull ScheduledFuture<?> scheduleWithFixedDelay(@NotNull Runnable command,
                                                              long initialDelay, long delay,
                                                              @NotNull TimeUnit unit) {
        return new ScheduleDelay(this, command, initialDelay, delay, unit);
    }

    @Override
    public CompletableFuture<Void> shutdownAsync() {
        if (terminatedFuture == null) {
            synchronized (this) {
                if (terminatedFuture == null) {
                    terminatedFuture = new CompletableFuture<>();
                    taskQueue.offer(() -> running = false);
                    LockSupport.unpark(idleThread);
                }
            }
        }
        return terminatedFuture;
    }

    @Override
    public void shutdown() {
        shutdownAsync();
    }

    @Override
    public @NotNull List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown() {
        return terminatedFuture != null;
    }

    @Override
    public boolean isTerminated() {
        return terminatedFuture.isDone();
    }

    @Override
    public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        try {
            shutdownAsync().get(timeout, unit);
            return true;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            return false;
        }
    }

    @Override
    public void execute(@NotNull Runnable command) {
        if (terminatedFuture != null) {
            throw new RejectedExecutionException("already shutdown");
        }
        taskQueue.offer(command);
        LockSupport.unpark(idleThread);
    }

    private interface TimeoutTask<V> extends ScheduledFuture<V> {
        Timeout timeout();

        Runnable task();

        default long time() {
            return timeout().time;
        }

        @Override
        default long getDelay(@NotNull TimeUnit targetUnit) {
            return timeout().getDelay(targetUnit);
        }

        @Override
        default int compareTo(@NotNull Delayed other) {
            if (other == this) // compare zero if same object
                return 0;
            if (other instanceof TimeoutTask<?> x) {
                return timeout().compareTo(x.timeout());
            }
            throw new IllegalArgumentException("should never happen");
        }
    }

    @SuppressWarnings("NullableProblems")
    private static class ScheduleOnce<T> implements TimeoutTask<T> {
        private final Timeout timeout;
        private final FutureTask<T> task;
        private final DefaultSingleThreadEventExecutor executor;

        private ScheduleOnce(DefaultSingleThreadEventExecutor executor,
                             FutureTask<T> task, long delay, TimeUnit unit) {
            this.executor = executor;
            this.task = task;
            this.timeout = new Timeout(executor, delay, unit);
            if (executor.inExecutor()) {
                executor.timeoutTasks.add(this);
            } else {
                executor.execute(() -> executor.timeoutTasks.add(this));
            }
        }

        @Override
        public Timeout timeout() {
            return timeout;
        }

        @Override
        public Runnable task() {
            return task;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (task.cancel(mayInterruptIfRunning)) {
                if (executor.inExecutor()) {
                    executor.timeoutTasks.remove(this);
                } else {
                    executor.execute(() -> executor.timeoutTasks.remove(this));
                }
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }

        @Override
        public boolean isDone() {
            return task.isDone();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return task.get();
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException
                , TimeoutException {
            return task.get(timeout, unit);
        }
    }

    @SuppressWarnings("NullableProblems")
    private static abstract class AbstractSchedule implements Runnable, TimeoutTask<Void> {
        protected final Runnable command;
        protected final long periodNanos;
        protected final DefaultSingleThreadEventExecutor executor;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicBoolean cancelled = new AtomicBoolean();
        protected Timeout timeout;
        private Throwable cause;

        private AbstractSchedule(DefaultSingleThreadEventExecutor executor,
                                 Runnable command,
                                 long delay, long period, TimeUnit unit) {
            this.executor = executor;
            this.command = command;
            this.periodNanos = unit.toNanos(period);
            this.timeout = new Timeout(executor, delay, unit);
            if (executor.inExecutor()) {
                executor.timeoutTasks.add(this);
            } else {
                executor.execute(() -> executor.timeoutTasks.add(this));
            }
        }

        @Override
        public Timeout timeout() {
            return timeout;
        }

        @Override
        public Runnable task() {
            return this;
        }

        @Override
        public void run() {
            try {
                if (isCancelled()) {
                    return;
                }
                doRun();
            } catch (Throwable t) {
                cause = t;
                latch.countDown();
                throw t;
            }
        }

        protected abstract void doRun();

        protected void nextRun(long delayNanos) {
            assert executor.inExecutor();
            this.timeout = new Timeout(executor, delayNanos, NANOSECONDS);
            executor.timeoutTasks.add(this);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (cancelled.compareAndSet(false, true)) {
                if (executor.inExecutor()) {
                    executor.timeoutTasks.remove(this);
                } else {
                    executor.execute(() -> executor.timeoutTasks.remove(this));
                }
                latch.countDown();
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public boolean isDone() {
            return latch.getCount() <= 0;
        }

        @Override
        public Void get() throws InterruptedException, ExecutionException {
            latch.await();
            if (isCancelled()) {
                throw new CancellationException();
            }
            if (cause != null) {
                throw new ExecutionException(cause);
            }
            throw new IllegalArgumentException("this should never happen");
        }

        @Override
        public Void get(long timeout, TimeUnit unit) throws InterruptedException,
                ExecutionException, TimeoutException {
            //noinspection ResultOfMethodCallIgnored
            latch.await(timeout, unit);
            if (isCancelled()) {
                throw new CancellationException();
            }
            if (cause != null) {
                throw new ExecutionException(cause);
            }
            throw new TimeoutException();
        }
    }

    private static class ScheduleDelay extends AbstractSchedule {

        private ScheduleDelay(DefaultSingleThreadEventExecutor executor, Runnable command,
                              long delay, long period, TimeUnit unit) {
            super(executor, command, delay, period, unit);
        }

        @Override
        protected void doRun() {
            command.run();
            nextRun(periodNanos);
        }
    }

    private static class ScheduleFixRate extends AbstractSchedule {

        private ScheduleFixRate(DefaultSingleThreadEventExecutor executor, Runnable command,
                                long delay, long period, TimeUnit unit) {
            super(executor, command, delay, period, unit);
        }

        @Override
        protected void doRun() {
            command.run();
            long nextTime = timeout().time + periodNanos;
            nextRun(nextTime - executor.now());
        }
    }

    private static class Timeout implements Comparable<Timeout> {
        private static final AtomicLong sequencer = new AtomicLong();
        private final DefaultSingleThreadEventExecutor executor;
        private final AtomicInteger status = new AtomicInteger();
        private final long time;
        private final long sequenceId;

        private Timeout(DefaultSingleThreadEventExecutor executor, long delay, TimeUnit unit) {
            this.executor = executor;
            this.time = unit.toNanos(delay) + executor.now();
            this.sequenceId = sequencer.incrementAndGet();
        }

        public long getDelay(TimeUnit unit) {
            return NANOSECONDS.convert(time - executor.now(), unit);
        }

        @Override
        public int compareTo(@NotNull DefaultSingleThreadEventExecutor.Timeout o) {
            int i = Long.compare(time, o.time);
            if (i == 0) {
                i = Long.compare(sequenceId, o.sequenceId);
            }
            return i;
        }
    }

    private class WorkerRunner implements Runnable {

        @Override
        public void run() {
            int maxMsgNumPerDrain = 100;
            while (running) {
                while (!timeoutTasks.isEmpty()) {
                    TimeoutTask<?> timeoutTask = timeoutTasks.peek();
                    long period = timeoutTask.time() - now();
                    if (period > 0) {
                        break;
                    }
                    var poll = timeoutTasks.poll();
                    assert timeoutTask == poll;
                    if (!poll.isCancelled()) {
                        try {
                            poll.task().run();
                        } catch (Throwable e) {
                            LOG.error("Unexpected task failed", e);
                        }
                    }
                }

                int counter = 0;
                while (running && counter < maxMsgNumPerDrain) {
                    Runnable task = taskQueue.relaxedPoll();
                    if (task == null) {
                        idleThread = thread;
                        task = taskQueue.poll();
                        if (task == null) {
                            TimeoutTask<?> timeoutTask = timeoutTasks.peek();
                            if (timeoutTask == null) {
                                LockSupport.park();
                            } else {
                                long nanos = timeoutTask.time() - now();
                                LockSupport.parkNanos(nanos);
                            }
                            idleThread = null;
                            break;
                        }
                    }
                    try {
                        task.run();
                    } catch (Throwable e) {
                        LOG.error("Unexpected task failed", e);
                    }
                    counter++;
                }
            }
            Runnable remain;
            while ((remain = taskQueue.poll()) != null) {
                try {
                    remain.run();
                } catch (Throwable e) {
                    LOG.error("Unexpected task failed", e);
                }
            }
            LOG.debug("Task shutting down");
            if (!timeoutTasks.isEmpty()) {
                timeoutTasks.poll().cancel(false);
            }
            assert terminatedFuture != null;
            terminatedFuture.complete(null);
        }
    }
}
