package io.axor.runtime.impl;

import io.axor.runtime.EventContext;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.EventDispatcherThread;
import io.masterkun.stateeasy.concurrent.DefaultSingleThreadEventExecutor;
import io.masterkun.stateeasy.concurrent.EventExecutorThreadFactory;
import io.masterkun.stateeasy.concurrent.ForwardingEventExecutor;
import io.masterkun.stateeasy.concurrent.SingleThreadEventExecutor;

public class DefaultEventDispatcher extends ForwardingEventExecutor implements EventDispatcher {

    private final SingleThreadEventExecutor executor;
    private final EventDispatcherThread thread;

    public DefaultEventDispatcher(String threadName) {
        var e = new DefaultSingleThreadEventExecutor(threadFactory(this, threadName));
        executor = e;
        //noinspection UnstableApiUsage
        thread = (EventDispatcherThread) e.getThread();
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

    @Override
    public EventContext setContext(EventContext context) {
        return thread.setContext(context);
    }

    @Override
    public EventContext getContext() {
        return thread.getContext();
    }
}
