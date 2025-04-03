package io.axor.runtime;

import io.axor.commons.concurrent.EventExecutorThread;

public class EventDispatcherThread extends EventExecutorThread implements EventDispatcher.DispatcherThread {
    private EventContext context = EventContext.INITIAL;

    public EventDispatcherThread(Runnable task, String name, EventDispatcher executor) {
        super(executor, task);
        setName(name);
        setDaemon(false);
    }

    @Override
    public EventDispatcher getOwnerExecutor() {
        return (EventDispatcher) super.getOwnerExecutor();
    }

    @Override
    public EventContext setContext(EventContext context) {
        EventContext prev = this.context;
        this.context = context == null ? EventContext.INITIAL : context;
        return prev;
    }

    @Override
    public EventContext getContext() {
        return context;
    }
}
