package io.axor.cp.raft.logging;

import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;
import io.axor.cp.messages.AppendStatus;
import io.axor.cp.messages.CommitStatus;
import io.axor.cp.messages.LogEntry;
import io.axor.cp.messages.LogEntryId;

import java.util.List;

public interface AsyncRaftLogging {

    LogEntryId startedId();

    LogEntryId commitedId();

    LogEntryId uncommitedId();

    EventStage<AppendStatus> append(LogEntry entry, EventPromise<AppendStatus> promise);

    EventStage<AppendStatus> append(List<LogEntry> entries, EventPromise<AppendStatus> promise);

    EventStage<CommitStatus> commit(LogEntryId commitAtId, EventPromise<CommitStatus> promise);

    EventStage<List<LogEntry>> read(LogEntryId startInclude, int entryLimit, int sizeLimit,
                                    EventPromise<List<LogEntry>> promise);

    EventStage<Void> resetUncommited(EventPromise<Void> promise);

    EventStage<Void> expire(LogEntryId before, EventPromise<Void> promise);
}
