package io.axor.raft.logging;

import io.axor.raft.RaftException;

import java.io.Closeable;

public interface RaftLoggingFactory extends Closeable {
    RaftLogging create(String name) throws RaftException;
}
