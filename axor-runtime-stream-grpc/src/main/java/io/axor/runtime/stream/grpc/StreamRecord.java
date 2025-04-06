package io.axor.runtime.stream.grpc;

import io.axor.runtime.EventContext;
import io.axor.runtime.Signal;

public interface StreamRecord<T> {
    record ContextMsg<T>(EventContext context, T msg) implements StreamRecord<T> {
    }

    record ContextSignal<T>(EventContext context, Signal signal) implements StreamRecord<T> {
    }
}
