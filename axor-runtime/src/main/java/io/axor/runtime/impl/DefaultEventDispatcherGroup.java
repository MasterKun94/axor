package io.axor.runtime.impl;

public class DefaultEventDispatcherGroup extends AbstractEventDispatcherGroup {

    public DefaultEventDispatcherGroup(String executorName, int threads, int groupId) {
        super(threads, i -> new DefaultEventDispatcher(executorName.formatted(groupId, i)));
    }
}
