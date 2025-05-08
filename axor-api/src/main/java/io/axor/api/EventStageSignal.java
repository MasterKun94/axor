package io.axor.api;

import io.axor.commons.concurrent.Try;
import io.axor.runtime.Signal;

import java.util.Objects;

public record EventStageSignal<T>(long tagId, Try<T> result) implements Signal {

    @SuppressWarnings("unchecked")
    public <P> EventStageSignal<P> as(Class<P> type) {
        if (result.isSuccess()) {
            if (Objects.requireNonNull(result.value()).getClass().isAssignableFrom(type)) {
                return (EventStageSignal<P>) this;
            } else {
                throw new UnsupportedOperationException("type mismatch");
            }
        } else {
            return (EventStageSignal<P>) this;
        }
    }

    public boolean isSuccess() {
        return result.isSuccess();
    }

    public boolean isFailure() {
        return result.isFailure();
    }

    public Throwable cause() {
        return result.cause();
    }

    public T value() {
        return result.value();
    }
}
