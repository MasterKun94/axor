package io.masterkun.kactor.runtime.impl;

import io.masterkun.kactor.runtime.EventDispatcher;
import io.masterkun.kactor.runtime.EventDispatcherThread;
import io.masterkun.kactor.runtime.EventDispatcherThreadFactory;
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
