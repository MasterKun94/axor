package io.axor.raft;

import com.google.protobuf.ByteString;

public record LogEntry(LogId id, ByteString data) {
    public LogEntry(long index, long term, ByteString data) {
        this(new LogId(index, term), data);
    }

    public LogEntry(long index, long term, String data) {
        this(new LogId(index, term), ByteString.copyFromUtf8(data));
    }
}
