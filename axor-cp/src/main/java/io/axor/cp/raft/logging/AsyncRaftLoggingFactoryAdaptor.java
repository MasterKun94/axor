package io.axor.cp.raft.logging;

import com.typesafe.config.Config;
import io.axor.commons.concurrent.DefaultSingleThreadEventExecutor;
import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventExecutorThread;
import io.axor.commons.concurrent.EventExecutorThreadFactory;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncRaftLoggingFactoryAdaptor implements AsyncRaftLoggingFactory {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private final RaftLoggingFactory factory;
    private final int instanceId;
    private final int writerExecutorNum;
    private final Map<Integer, EventExecutor> writerExecutors;
    private final ExecutorService readerExecutor;
    private final AtomicInteger writerExecutorCounter = new AtomicInteger();

    public AsyncRaftLoggingFactoryAdaptor(RaftLoggingFactory factory,
                                          Config config) {
        this.factory = factory;
        this.writerExecutorNum = config.getInt("asyncWriterThreadNum");
        this.writerExecutors = new ConcurrentHashMap<>();
        this.instanceId = COUNTER.getAndIncrement();
        int readerExecutorNum = config.getInt("asyncReaderThreadNum");
        this.readerExecutor = Executors.newFixedThreadPool(readerExecutorNum, Thread.ofPlatform()
                .name("RaftLoggingReader-" + instanceId + "-", 0)
                .factory());
    }

    private EventExecutor nextWriterExecutor() {
        int id = writerExecutorCounter.getAndIncrement() % writerExecutorNum;
        return writerExecutors.computeIfAbsent(id, cnt -> {
            EventExecutorThreadFactory factory = (executor, r) -> {
                String name = "RaftLoggingWriter-" + instanceId + "-" + cnt;
                return new EventExecutorThread(executor, r, name);
            };
            return new DefaultSingleThreadEventExecutor(factory);
        });
    }

    @Override
    public EventStage<AsyncRaftLogging> create(String name,
                                               EventPromise<AsyncRaftLogging> promise) {
        EventExecutor executor = nextWriterExecutor();
        executor.execute(() -> {
            RaftLogging logging;
            try {
                logging = factory.create(name);
                promise.success(new AsyncRaftLoggingAdaptor(logging, executor, readerExecutor));
            } catch (Exception e) {
                promise.failure(e);
            }
        });
        return promise;
    }
}
