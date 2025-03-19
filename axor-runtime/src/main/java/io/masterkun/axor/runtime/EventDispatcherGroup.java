package io.masterkun.axor.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EventDispatcherGroup extends Iterable<EventDispatcher> {

    EventDispatcher nextDispatcher();

    default CompletableFuture<Void> shutdownAsync() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (EventDispatcher executor : this) {
            futures.add(executor.shutdownAsync());
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}
