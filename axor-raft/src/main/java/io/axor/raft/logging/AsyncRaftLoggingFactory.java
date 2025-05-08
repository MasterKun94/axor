package io.axor.raft.logging;

import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;

public interface AsyncRaftLoggingFactory {
    EventStage<AsyncRaftLogging> create(String name, EventPromise<AsyncRaftLogging> promise);
}
