package io.masterkun.kactor.runtime.impl;

public class DefaultEventDispatcherGroup extends AbstractEventDispatcherGroup {

    public DefaultEventDispatcherGroup(String executorName, int threads) {
        super(threads, i -> new DefaultEventDispatcher(executorName.formatted(i)));
    }
}
