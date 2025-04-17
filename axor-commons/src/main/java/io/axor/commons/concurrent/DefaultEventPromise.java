package io.axor.commons.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class DefaultEventPromise<T> implements EventPromise<T> {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultEventPromise.class);
    private static final byte pending = 0;
    private static final byte success = 1;
    private static final byte failure = 2;
    private static final byte cancel = 3;
    private final EventExecutor executor;
    protected Object obj;
    protected volatile byte status = pending;
    protected List<EventStageObserver<T>> listeners;
    protected EventStageObserver<T> listener;

    DefaultEventPromise(EventExecutor eventExecutor) {
        this.executor = Objects.requireNonNull(eventExecutor, "eventExecutor");
    }

    public static <T> DefaultEventPromise<T> create(EventExecutor executor) {
        return new DefaultEventPromise<>(executor);
    }

    public static <T> DefaultEventPromise<T> success(T value, EventExecutor executor) {
        DefaultEventPromise<T> promise = new DefaultEventPromise<>(executor);
        promise.doSuccess(value);
        return promise;
    }

    public static <T> DefaultEventPromise<T> failure(Throwable error, EventExecutor executor) {
        DefaultEventPromise<T> promise = new DefaultEventPromise<>(executor);
        promise.doFailed(error);
        return promise;
    }

    @Override
    public void success(T value) {
        if (executor.inExecutor()) {
            doSuccess(value);
        } else {
            executor.execute(() -> doSuccess(value));
        }
    }

    private <P> void doFireListener(P param, BiConsumer<EventStageObserver<T>, P> handler) {
        if (listener != null) {
            assert listeners == null;
            try {
                handler.accept(listener, param);
            } catch (Throwable e) {
                try {
                    listener.failure(e);
                } catch (Throwable e0) {
                    LOG.error("Listener unexpected throw error [{}] and handle failed", e, e0);
                }
            }
            listener = null;
        } else if (listeners != null && !listeners.isEmpty()) {
            for (EventStageObserver<T> listener : listeners) {
                try {
                    handler.accept(listener, param);
                } catch (Throwable e) {
                    try {
                        listener.failure(e);
                    } catch (Throwable e0) {
                        LOG.error("Listener unexpected throw error [{}] and handle failed", e, e0);
                    }
                }
            }
            listeners = null;
        }
    }

    protected boolean doSuccess(T value) {
        if (status != pending) {
            return false;
        }
        this.status = success;
        this.obj = value;
        doFireListener(value, EventStageObserver::success);
        return true;
    }

    @Override
    public void failure(Throwable cause) {
        if (executor.inExecutor()) {
            doFailed(cause);
        } else {
            executor.execute(() -> doFailed(cause));
        }
    }

    protected boolean doFailed(Throwable cause) {
        if (status != pending) {
            return false;
        }
        this.status = cause instanceof CancellationException ? cancel : failure;
        this.obj = cause;
        doFireListener(cause, EventStageObserver::failure);
        return true;
    }

    @Override
    public boolean cancel() {
        if (isDone()) {
            return false;
        }
        failure(new CancellationException());
        return true;
    }

    protected <P> EventPromise<P> newPromise(EventExecutor executor) {
        return new DefaultEventPromise<>(executor);
    }

    @Override
    public EventPromise<T> observe(EventStageObserver<T> observer) {
        if (executor.inExecutor()) {
            doAddListener(observer);
        } else {
            executor.execute(() -> doAddListener(observer));
        }
        return this;
    }

    @Override
    public EventPromise<T> observe(Collection<EventStageObserver<T>> observers) {
        if (executor.inExecutor()) {
            doAddListeners(observers);
        } else {
            executor.execute(() -> doAddListeners(observers));
        }
        return this;
    }

    private void doAddListener(EventStageObserver<T> listener) {
        if (this.listener != null) {
            this.listeners = new ArrayList<>(List.of(this.listener, listener));
            this.listener = null;
        } else if (this.listeners == null) {
            this.listener = listener;
        } else {
            this.listeners.add(listener);
        }
        maybeFireListeners();
    }

    private void doAddListeners(Collection<EventStageObserver<T>> listeners) {
        if (listener != null) {
            this.listeners = new ArrayList<>(listeners.size() + 1);
            this.listeners.add(listener);
            this.listeners.addAll(listeners);
            listener = null;
        } else if (this.listeners == null) {
            this.listeners = new ArrayList<>(listeners);
        } else {
            this.listeners.addAll(listeners);
        }
        maybeFireListeners();
    }

    private void maybeFireListeners() {
        if (status != pending) {
            switch (status) {
                case success:
                    doFireListener((T) obj, EventStageObserver::success);
                    break;
                case failure, cancel:
                    doFireListener((Throwable) obj, EventStageObserver::failure);
                    break;
                default:
                    throw new IllegalArgumentException("illegal status: " + status);
            }
        }
    }

    @Override
    public boolean isDone() {
        return status != pending;
    }

    @Override
    public boolean isCancelled() {
        return status == cancel;
    }

    @Override
    public boolean isFailure() {
        return status == failure;
    }

    @Override
    public Try<T> getResult() {
        return switch (status) {
            case pending -> null;
            case success -> Try.success((T) obj);
            case failure, cancel -> Try.failure((Throwable) obj);
            default -> throw new IllegalArgumentException("illegal status: " + status);
        };
    }

    @Override
    public boolean isSuccess() {
        return status == success;
    }

    @Override
    public EventExecutor executor() {
        return executor;
    }

    @Override
    public EventStage<T> executor(EventExecutor executor) {
        if (executor.equals(this.executor)) {
            return this;
        } else {
            return map(Function.identity(), executor);
        }
    }

    protected EventStage<T> toCompletedStage(EventExecutor executor) {
        assert getResult() != null;
        return switch (getResult()) {
            case Success<T>(T value) -> new SucceedEventStage<>(value, executor);
            case Failure<T>(Throwable e) -> new FailedEventStage<T>(e, executor);
            case null -> throw new RuntimeException("should never happen");
        };
    }

    @Override
    public <P> EventStage<P> map(Function<T, P> func, EventExecutor executor) {
        if (isDone()) {
            return toCompletedStage(executor).map(func);
        }
        EventPromise<P> promise = newPromise(executor);
        observe(new EventStageObserver<>() {
            @Override
            public void success(T value) {
                promise.success(func.apply(value));
            }

            @Override
            public void failure(Throwable cause) {
                promise.failure(cause);
            }
        });
        return promise;
    }

    @Override
    public <P> EventStage<P> flatmap(Function<T, EventStage<P>> func, EventExecutor executor) {
        if (isDone()) {
            return toCompletedStage(executor).flatmap(func);
        }
        EventPromise<P> promise = newPromise(executor);
        observe(new EventStageObserver<>() {
            @Override
            public void success(T value) {
                func.apply(value).observe(promise);
            }

            @Override
            public void failure(Throwable cause) {
                promise.failure(cause);
            }
        });
        return promise;
    }

    @Override
    public <P> EventStage<P> transform(Function<Try<T>, Try<P>> transformer,
                                       EventExecutor executor) {
        if (isDone()) {
            return toCompletedStage(executor).transform(transformer);
        }
        EventPromise<P> promise = newPromise(executor);
        observe(new EventStageObserver<>() {
            @Override
            public void success(T value) {
                transformer.apply(Try.success(value)).notify(promise);
            }

            @Override
            public void failure(Throwable cause) {
                transformer.apply(Try.failure(cause)).notify(promise);
            }
        });
        return promise;
    }

    @Override
    public <P> EventStage<P> flatTransform(Function<Try<T>, EventStage<P>> transformer,
                                           EventExecutor executor) {
        if (isDone()) {
            return toCompletedStage(executor).flatTransform(transformer);
        }
        EventPromise<P> promise = newPromise(executor);
        observe(new EventStageObserver<>() {
            @Override
            public void success(T value) {
                transformer.apply(Try.success(value)).observe(promise);
            }

            @Override
            public void failure(Throwable cause) {
                transformer.apply(Try.failure(cause)).observe(promise);
            }
        });
        return promise;
    }

    @Override
    public EventFuture<T> toFuture() {
        return EventPromise.super.toFuture();
    }
}
