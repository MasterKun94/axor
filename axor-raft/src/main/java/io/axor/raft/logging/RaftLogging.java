package io.axor.raft.logging;

import io.axor.raft.RaftException;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.AppendResult;
import io.axor.raft.proto.PeerProto.CommitResult;
import io.axor.raft.proto.PeerProto.LogEntry;
import io.axor.raft.proto.PeerProto.LogId;

import java.util.List;

public interface RaftLogging {
    LogId INITIAL_LOG_ID = LogId.getDefaultInstance();

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

    void installSnapshot(PeerProto.InstallSnapshot installSnapshot) throws RaftException;

    void addListener(LogEntryListener listener);

    interface LogEntryListener {
        void appended(LogEntry entry);

        void removed(LogId id);

        void commited(LogId id);
    }
}
