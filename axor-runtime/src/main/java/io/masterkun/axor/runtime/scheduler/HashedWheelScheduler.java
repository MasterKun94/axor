package io.masterkun.axor.runtime.scheduler;

import java.io.Closeable;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class HashedWheelScheduler implements Scheduler, Closeable {

    private final Timer timer;

    public HashedWheelScheduler(HashedWheelTimer timer) {
        this.timer = timer;
        timer.start();
    }

    @Override
    public <T> CompletableFuture<T> setTimeout(CompletableFuture<T> future, long delay, TimeUnit unit) {
        if (future.isDone()) {
            return future;
        }
        Timeout timeout = timer.newTimeout(t -> future.completeExceptionally(new TimeoutException()), delay, unit);
        return future.whenComplete((t, e) -> {
            if (timeout.isExpired()) {
                return;
            }
            if (!timeout.isCancelled()) {
                timeout.cancel();
            }
        });
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit, ExecutorService executor) {
        FutureTask<?> task = new FutureTask<>(command, null);
        Timeout timeout = executor == null ?
                timer.newTimeout(t -> task.run(), delay, unit) :
                timer.newTimeout(t -> executor.execute(task), delay, unit);
        return new ScheduleOnce<>(timeout, task, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit, ExecutorService executor) {
        FutureTask<V> task = new FutureTask<>(callable);
        Timeout timeout = executor == null ?
                timer.newTimeout(t -> task.run(), delay, unit) :
                timer.newTimeout(t -> executor.execute(task), delay, unit);
        return new ScheduleOnce<>(timeout, task, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit, ExecutorService executor) {
        ScheduleFixRate scheduleFixRate = new ScheduleFixRate(timer, executor, command, initialDelay, period, unit);
        timer.newTimeout(scheduleFixRate, initialDelay, unit);
        return scheduleFixRate;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit, ExecutorService executor) {
        ScheduleDelay scheduleDelay = new ScheduleDelay(timer, executor, command, initialDelay, delay, unit);
        timer.newTimeout(scheduleDelay, initialDelay, unit);
        return scheduleDelay;
    }

    @Override
    public void close() {
        timer.stop();
    }

    @SuppressWarnings("NullableProblems")
    private static class ScheduleOnce<T> implements ScheduledFuture<T> {
        private final Timeout timeout;
        private final FutureTask<T> task;
        private final long time;

        private ScheduleOnce(Timeout timeout, FutureTask<T> task, long delay, TimeUnit unit) {
            this.timeout = timeout;
            this.task = task;
            this.time = unit.toMillis(delay) + System.currentTimeMillis();
        }

        @Override
        public long getDelay(TimeUnit targetUnit) {
            return targetUnit.convert(
                    time - System.currentTimeMillis(),
                    TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            if (other == this) // compare zero if same object
                return 0;
            if (other instanceof ScheduleOnce<?> x) {
                return Long.compare(time, x.time);
            }
            if (other instanceof ScheduleDelay x) {
                return Long.compare(time, x.time);
            }
            if (other instanceof ScheduleFixRate x) {
                return Long.compare(time, x.time);
            }
            return Long.compare(getDelay(MILLISECONDS), other.getDelay(MILLISECONDS));
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return timeout.cancel() || task.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return timeout.isCancelled() || task.isCancelled();
        }

        @Override
        public boolean isDone() {
            return timeout.isExpired() && task.isDone();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return task.get();
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return task.get(timeout, unit);
        }
    }

    @SuppressWarnings("NullableProblems")
    private static abstract class AbstractSchedule implements TimerTask, Runnable, ScheduledFuture<Void> {
        protected final Runnable command;
        protected final long periodMillis;
        private final Timer timer;
        private final ExecutorService executor;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicBoolean cancelled = new AtomicBoolean();
        protected volatile long time;
        protected Timeout timeout;
        private Throwable cause;

        private AbstractSchedule(Timer timer,
                                 ExecutorService executor,
                                 Runnable command,
                                 long delay, long period, TimeUnit unit) {
            this.timer = timer;
            this.executor = executor;
            this.command = command;
            this.periodMillis = unit.toMillis(period);
            this.time = unit.toMillis(delay) + System.currentTimeMillis();
        }

        @Override
        public void run(Timeout timeout) {
            if (executor == null) {
                run();
            } else {
                executor.execute(this);
            }
        }

        @Override
        public void run() {
            try {
                if (isCancelled()) {
                    return;
                }
                doRun();
            } catch (Throwable t) {
                latch.countDown();
                cause = t;
                throw t;
            }
        }

        protected abstract void doRun();

        protected void nextRun(long timeoutMills, long nextTimeMills) {
            time = nextTimeMills;
            timeout = timer.newTimeout(this, timeoutMills, MILLISECONDS);
        }

        @Override
        public long getDelay(TimeUnit targetUnit) {
            return targetUnit.convert(
                    time - System.currentTimeMillis(),
                    TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            if (other == this) // compare zero if same object
                return 0;
            if (other instanceof ScheduleDelay x) {
                return Long.compare(time, x.time);
            }
            if (other instanceof ScheduleOnce<?> x) {
                return Long.compare(time, x.time);
            }
            if (other instanceof ScheduleFixRate x) {
                return Long.compare(time, x.time);
            }
            return Long.compare(getDelay(MILLISECONDS), other.getDelay(MILLISECONDS));
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (cancelled.compareAndSet(false, true)) {
                latch.countDown();
                if (timeout != null) {
                    timeout.cancel();
                }
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
        public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
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

        private ScheduleDelay(Timer timer, ExecutorService executor, Runnable command, long delay, long period, TimeUnit unit) {
            super(timer, executor, command, delay, period, unit);
        }

        @Override
        protected void doRun() {
            command.run();
            nextRun(periodMillis, System.currentTimeMillis() + periodMillis);
        }
    }

    private static class ScheduleFixRate extends AbstractSchedule {

        private ScheduleFixRate(Timer timer, ExecutorService executor, Runnable command, long delay, long period, TimeUnit unit) {
            super(timer, executor, command, delay, period, unit);
        }

        @Override
        protected void doRun() {
            command.run();
            long nextTime = periodMillis + time;
            nextRun(nextTime - System.currentTimeMillis(), nextTime);
        }
    }
}
