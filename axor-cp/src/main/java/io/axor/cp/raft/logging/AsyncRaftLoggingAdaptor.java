package io.axor.cp.raft.logging;

import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;
import io.axor.cp.messages.AppendStatus;
import io.axor.cp.messages.CommitStatus;
import io.axor.cp.messages.LogEntry;
import io.axor.cp.messages.LogEntryId;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class AsyncRaftLoggingAdaptor implements AsyncRaftLogging {
    private final RaftLogging logging;
    private final EventExecutor writerExecutor;
    private final ExecutorService readerExecutor;

    public AsyncRaftLoggingAdaptor(RaftLogging logging, EventExecutor writerExecutor,
                                   ExecutorService readerExecutor) {
        this.logging = logging;
        this.writerExecutor = writerExecutor;
        this.readerExecutor = readerExecutor;
    }

    @Override
    public LogEntryId startedId() {
        return logging.startedId();
    }

    @Override
    public LogEntryId commitedId() {
        return logging.commitedId();
    }

    @Override
    public LogEntryId uncommitedId() {
        return logging.uncommitedId();
    }

    @Override
    public EventStage<AppendStatus> append(LogEntry entry, EventPromise<AppendStatus> promise) {
        writerExecutor.execute(() -> {
            try {
                promise.success(logging.append(entry));
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise;
    }

    @Override
    public EventStage<AppendStatus> append(List<LogEntry> entries,
                                           EventPromise<AppendStatus> promise) {
        writerExecutor.execute(() -> {
            try {
                promise.success(logging.append(entries));
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise;
    }

    @Override
    public EventStage<CommitStatus> commit(LogEntryId commitAtId,
                                           EventPromise<CommitStatus> promise) {
        writerExecutor.execute(() -> {
            try {
                promise.success(logging.commit(commitAtId));
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise;
    }

    @Override
    public EventStage<List<LogEntry>> read(LogEntryId startInclude, int entryLimit, int sizeLimit
            , EventPromise<List<LogEntry>> promise) {
        readerExecutor.execute(() -> {
            try {
                promise.success(logging.read(startInclude, entryLimit, sizeLimit));
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise;
    }

    @Override
    public EventStage<Void> resetUncommited(EventPromise<Void> promise) {
        writerExecutor.execute(() -> {
            try {
                logging.resetUncommited();
                promise.success(null);
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise;
    }

    @Override
    public EventStage<Void> expire(LogEntryId before, EventPromise<Void> promise) {
        writerExecutor.execute(() -> {
            try {
                logging.expire(before);
                promise.success(null);
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise;
    }
}
