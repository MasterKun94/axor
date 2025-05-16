package io.axor.raft.logging;

import io.axor.raft.RaftException;

import java.io.Closeable;

public interface RaftLoggingFactory extends Closeable {
    RaftLogging createLogging(String name) throws RaftException;

    SnapshotStore createSnapshotStore(String name) throws RaftException;
}
