package io.axor.raft.logging;

import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventStage;
import io.axor.raft.RaftException;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.AppendResult;
import io.axor.raft.proto.PeerProto.CommitResult;
import io.axor.raft.proto.PeerProto.LogEntry;
import io.axor.raft.proto.PeerProto.LogId;
import io.axor.raft.proto.PeerProto.Snapshot;

import java.util.List;

public interface RaftLogging {
    LogId INITIAL_LOG_ID = LogId.newBuilder().setTerm(0).setIndex(0).build();

    LogId startedId();

    LogId commitedId();

    List<LogId> uncommitedId();

    LogId logEndId();

    AppendResult append(LogId prevLogId, LogEntry entry) throws RaftException;

    AppendResult append(LogId prevLogId, List<LogEntry> entries) throws RaftException;

    CommitResult commit(LogId commitAtId) throws RaftException;

    List<LogEntry> read(LogId start, boolean includeStart, boolean includeUncommited,
                        int entryLimit, int sizeLimit) throws RaftException;

    List<LogEntry> readForSync(LogId commited, List<LogId> uncommited, int entryLimit,
                               int sizeLimit) throws RaftException;

    void resetUncommited() throws RaftException;

    void expire(LogId before) throws RaftException;

    void loadSnapshot(Snapshot snapshot) throws RaftException;

    void installSnapshot(PeerProto.InstallSnapshot snapshot) throws RaftException;

    EventStage<Snapshot> takeSnapshot(Snapshot snapshot,
                                      EventExecutor executor);

    void addListener(LogEntryListener listener);

    interface LogEntryListener {
        void appended(LogEntry entry) throws RaftException;

        void removed(LogId id) throws RaftException;

        void commited(LogId id) throws RaftException;

        default void loadSnapshot(Snapshot snapshot) throws RaftException {
        }

        default void installSnapshot(PeerProto.InstallSnapshot snapshot) throws RaftException {
        }

        default EventStage<Snapshot> takeSnapshot(Snapshot snapshot, EventExecutor executor) {
            return EventStage.succeed(snapshot, executor);
        }
    }
}
