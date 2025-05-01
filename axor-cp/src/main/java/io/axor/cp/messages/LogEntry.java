package io.axor.cp.messages;

import com.google.protobuf.ByteString;

public record LogEntry(LogEntryId id, ByteString data) {
    public LogEntry(long index, long term, ByteString data) {
        this(new LogEntryId(index, term), data);
    }

    public LogEntry(long index, long term, String data) {
        this(new LogEntryId(index, term), ByteString.copyFromUtf8(data));
    }
}
