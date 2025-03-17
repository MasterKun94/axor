package io.masterkun.axor.runtime;

import io.masterkun.stateeasy.concurrent.EventExecutor;
import io.masterkun.stateeasy.concurrent.EventExecutorThread;
import io.masterkun.stateeasy.concurrent.EventExecutorThreadFactory;
import org.jetbrains.annotations.NotNull;

public interface EventDispatcherThreadFactory extends EventExecutorThreadFactory {
    EventDispatcherThread newThread(EventDispatcher eventExecutor, @NotNull Runnable runnable);

    @Override
    default EventExecutorThread newThread(EventExecutor eventExecutor, @NotNull Runnable runnable) {
        return newThread((EventDispatcher) eventExecutor, runnable);
    }
}
