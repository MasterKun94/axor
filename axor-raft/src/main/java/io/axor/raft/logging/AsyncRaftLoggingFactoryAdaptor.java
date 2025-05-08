package io.axor.raft.logging;

import io.axor.commons.concurrent.DefaultSingleThreadEventExecutor;
import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventExecutorThread;
import io.axor.commons.concurrent.EventExecutorThreadFactory;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;

import java.util.concurrent.atomic.AtomicInteger;

public class AsyncRaftLoggingFactoryAdaptor implements AsyncRaftLoggingFactory {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private final RaftLoggingFactory factory;
    private final int instanceId;
    private final AtomicInteger writerExecutorCounter = new AtomicInteger();

    public AsyncRaftLoggingFactoryAdaptor(RaftLoggingFactory factory) {
        this.factory = factory;
        this.instanceId = COUNTER.getAndIncrement();
    }

    private EventExecutor nextExecutor() {
        int id = writerExecutorCounter.getAndIncrement();
        EventExecutorThreadFactory factory = (executor, r) -> {
            String name = "RaftLoggingExecutor-" + instanceId + "-" + id;
            return new EventExecutorThread(executor, r, name);
        };
        return new DefaultSingleThreadEventExecutor(factory);
    }

    @Override
    public EventStage<AsyncRaftLogging> create(String name,
                                               EventPromise<AsyncRaftLogging> promise) {
        EventExecutor executor = nextExecutor();
        executor.execute(() -> {
            RaftLogging logging;
            try {
                logging = factory.create(name);
                promise.success(new AsyncRaftLoggingAdaptor(logging, executor));
            } catch (Exception e) {
                promise.failure(e);
            }
        });
        return promise;
    }
}
