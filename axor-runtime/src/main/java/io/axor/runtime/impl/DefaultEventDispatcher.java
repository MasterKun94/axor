package io.axor.runtime.impl;

import io.axor.runtime.EventDispatcher;
import io.axor.runtime.EventDispatcherThread;
import io.masterkun.stateeasy.concurrent.DefaultSingleThreadEventExecutor;
import io.masterkun.stateeasy.concurrent.EventExecutorThreadFactory;
import io.masterkun.stateeasy.concurrent.ForwardingEventExecutor;
import io.masterkun.stateeasy.concurrent.SingleThreadEventExecutor;

public class DefaultEventDispatcher extends ForwardingEventExecutor implements EventDispatcher {

    private final SingleThreadEventExecutor executor;

    public DefaultEventDispatcher(String threadName) {
        executor = new DefaultSingleThreadEventExecutor(threadFactory(this, threadName));
    }

    @SuppressWarnings("InstantiatingAThreadWithDefaultRunMethod")
    private static EventExecutorThreadFactory threadFactory(EventDispatcher dispatcher,
                                                            String threadName) {
        return (eventExecutor, runnable) ->
                new EventDispatcherThread(runnable, threadName, dispatcher);
    }

    @Override
    protected SingleThreadEventExecutor delegate() {
        return executor;
    }
}
