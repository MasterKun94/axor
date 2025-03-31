package io.axor.runtime.stream.grpc;

import io.axor.runtime.EventContext;

public record ContextMsg<T>(EventContext context, T msg) {
}
