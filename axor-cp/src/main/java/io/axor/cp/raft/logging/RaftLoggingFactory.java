package io.axor.cp.raft.logging;

import io.axor.cp.raft.RaftException;

import java.io.Closeable;

public interface RaftLoggingFactory extends Closeable {
    RaftLogging create(String name) throws RaftException;
}
