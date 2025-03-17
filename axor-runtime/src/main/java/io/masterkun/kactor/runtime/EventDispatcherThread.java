package io.masterkun.kactor.runtime;

import io.masterkun.stateeasy.concurrent.EventExecutorThread;

public class EventDispatcherThread extends EventExecutorThread implements EventDispatcher.DispatcherThread {

    public EventDispatcherThread(Runnable task, String name, EventDispatcher executor) {
        super(executor, task);
        setName(name);
        setDaemon(false);
    }

    @Override
    public EventDispatcher getOwnerExecutor() {
        return (EventDispatcher) super.getOwnerExecutor();
    }
}
