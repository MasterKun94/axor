package io.axor.raft.logging;

import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;
import io.axor.raft.LogEntry;
import io.axor.raft.LogId;

import java.util.List;

public interface AsyncRaftLogging {

    LogId startedId();

    LogId commitedId();

    List<LogId> uncommitedId();

    LogId logEndId();

    EventStage<AppendResult> append(LogEntry entry, EventPromise<AppendResult> promise);

    EventStage<AppendResult> append(List<LogEntry> entries, EventPromise<AppendResult> promise);

    EventStage<CommitResult> commit(LogId commitAtId, EventPromise<CommitResult> promise);

    EventStage<List<LogEntry>> read(LogId start, boolean includeStart, boolean includeUncommited,
                                    int entryLimit, int sizeLimit,
                                    EventPromise<List<LogEntry>> promise);

    EventStage<List<LogEntry>> readForSync(LogId commited, List<LogId> uncommited, int entryLimit,
                                           int sizeLimit, EventPromise<List<LogEntry>> promise);

    EventStage<Void> resetUncommited(EventPromise<Void> promise);

    EventStage<Void> expire(LogId before, EventPromise<Void> promise);
}
