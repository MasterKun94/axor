package io.axor.cp.raft.logging;

import io.axor.cp.messages.AppendStatus;
import io.axor.cp.messages.CommitStatus;
import io.axor.cp.messages.LogEntry;
import io.axor.cp.messages.LogEntryId;
import io.axor.cp.raft.RaftException;

import java.io.Closeable;
import java.util.Iterator;
import java.util.List;

public interface RaftLogging {
    LogEntryId startedId();

    LogEntryId commitedId();

    LogEntryId uncommitedId();

    AppendStatus append(LogEntry entry) throws RaftException;

    AppendStatus append(List<LogEntry> entries) throws RaftException;

    CommitStatus commit(LogEntryId commitAtId) throws RaftException;

    List<LogEntry> read(LogEntryId startInclude, int entryLimit, int sizeLimit) throws RaftException;

    void resetUncommited() throws RaftException;

    void expire(LogEntryId before) throws RaftException;

    interface Iter extends Iterator<LogEntry>, Closeable {
    }
}
