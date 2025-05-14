package io.axor.flow;

public sealed interface FlowEvent<T> {
    record Item<T>(T item) implements FlowEvent<T> {
    }

    record Complete<T>() implements FlowEvent<T> {
    }

    record Error<T>(Throwable error) implements FlowEvent<T> {
    }
}
