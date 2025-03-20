package io.masterkun.axor.api.impl;

import io.masterkun.axor.api.SystemEvent;

import java.util.List;

public interface Watchable {

    void addWatcher(LocalActorRef<?> watcher, List<Class<? extends SystemEvent>> watchEvents);

    void removeWatcher(LocalActorRef<?> watcher);
}
