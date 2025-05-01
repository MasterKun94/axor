package io.axor.cp.messages;

import com.google.protobuf.ByteString;

public record LogEntry(LogEntryId id, ByteString data) {
}
