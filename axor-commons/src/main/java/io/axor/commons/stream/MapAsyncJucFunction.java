package io.axor.commons.stream;

import java.util.concurrent.CompletableFuture;

public interface MapAsyncJucFunction<T, P> {
    CompletableFuture<P> map(T data) throws Throwable;
}
