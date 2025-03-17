package io.masterkun.axor.runtime.impl;

import io.masterkun.axor.runtime.EventDispatcher;
import io.masterkun.axor.runtime.EventDispatcherThread;
import io.masterkun.axor.runtime.EventDispatcherThreadFactory;
import io.masterkun.stateeasy.concurrent.DefaultSingleThreadEventExecutor;

public class DefaultEventDispatcher extends DefaultSingleThreadEventExecutor implements EventDispatcher {

    public DefaultEventDispatcher(String threadName) {
        super(threadFactory(threadName));
    }

    @SuppressWarnings("InstantiatingAThreadWithDefaultRunMethod")
    private static EventDispatcherThreadFactory threadFactory(String threadName) {
        return (eventExecutor, runnable) ->
                new EventDispatcherThread(runnable, threadName, eventExecutor);
    }
}
