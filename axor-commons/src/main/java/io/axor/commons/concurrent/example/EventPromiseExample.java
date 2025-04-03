package io.axor.commons.concurrent.example;

import io.axor.commons.concurrent.DefaultSingleThreadEventExecutor;
import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;
import io.axor.commons.concurrent.Try;

public class EventPromiseExample {
    private static final EventExecutor mainExecutor = new DefaultSingleThreadEventExecutor();
    private static final EventExecutor taskExecutor = new DefaultSingleThreadEventExecutor();

    public static void main(String[] args) {
        Try<String> result = runAsync(EventPromise.newPromise(mainExecutor))
                .map(str -> str + " World")
                .toFuture()
                .syncUninterruptibly();
        System.out.println(result);
        mainExecutor.shutdown();
        taskExecutor.shutdown();
    }

    private static EventStage<String> runAsync(EventPromise<String> promise) {
        taskExecutor.execute(() -> {
            try {
                // running task
                Thread.sleep(100);
                promise.success("Hello");
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise;
    }
}
