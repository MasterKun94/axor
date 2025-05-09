package io.axor.raft.logging;

import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;
import io.axor.raft.proto.PeerProto.AppendResult;
import io.axor.raft.proto.PeerProto.CommitResult;
import io.axor.raft.proto.PeerProto.LogEntry;
import io.axor.raft.proto.PeerProto.LogId;

import java.util.List;

public class AsyncRaftLoggingAdaptor implements AsyncRaftLogging {
    private final RaftLogging logging;
    private final EventExecutor executor;

    public AsyncRaftLoggingAdaptor(RaftLogging logging, EventExecutor executor) {
        this.logging = logging;
        this.executor = executor;
    }

    @Override
    public LogId startedId() {
        return logging.startedId();
    }

    @Override
    public LogId commitedId() {
        return logging.commitedId();
    }

    @Override
    public List<LogId> uncommitedId() {
        return logging.uncommitedId();
    }

    @Override
    public LogId logEndId() {
        return logging.logEndId();
    }

    @Override
    public EventStage<AppendResult> append(LogEntry entry, EventPromise<AppendResult> promise) {
        executor.execute(() -> {
            try {
                promise.success(logging.append(entry));
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise;
    }

    @Override
    public EventStage<AppendResult> append(List<LogEntry> entries,
                                           EventPromise<AppendResult> promise) {
        executor.execute(() -> {
            try {
                promise.success(logging.append(entries));
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise;
    }

    @Override
    public EventStage<CommitResult> commit(LogId commitAtId,
                                           EventPromise<CommitResult> promise) {
        executor.execute(() -> {
            try {
                promise.success(logging.commit(commitAtId));
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise;
    }

    @Override
    public EventStage<List<LogEntry>> read(LogId start, boolean includeStart,
                                           boolean includeUncommited, int entryLimit,
                                           int sizeLimit, EventPromise<List<LogEntry>> promise) {
        executor.execute(() -> {
            try {
                promise.success(logging.read(start, includeStart, includeUncommited, entryLimit,
                        sizeLimit));
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise;
    }

    @Override
    public EventStage<List<LogEntry>> readForSync(LogId commited, List<LogId> uncommited,
                                                  int entryLimit, int sizeLimit,
                                                  EventPromise<List<LogEntry>> promise) {
        executor.execute(() -> {
            try {
                promise.success(logging.readForSync(commited, uncommited, entryLimit, sizeLimit));
            } catch (Throwable e) {
                promise.failure(e);
            }
        });
        return promise;
    }

    @Override
    public EventStage<Void> resetUncommited(EventPromise<Void> promise) {
        executor.execute(() -> {
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
    public EventStage<Void> expire(LogId before, EventPromise<Void> promise) {
        executor.execute(() -> {
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
