package io.axor.runtime.impl;

import io.axor.commons.concurrent.DefaultSingleThreadEventExecutor;
import io.axor.commons.concurrent.EventExecutorThreadFactory;
import io.axor.commons.concurrent.ForwardingEventExecutor;
import io.axor.commons.concurrent.SingleThreadEventExecutor;
import io.axor.runtime.EventContext;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.EventDispatcherThread;

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
