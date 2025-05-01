package io.axor.cp.messages;

import java.util.List;

public record LogFetchRes(long txnId, boolean success, List<LogEntry> entries,
                          String msg) implements ServiceMessage {
    public static LogFetchRes success(long txnId, List<LogEntry> entries) {
        return new LogFetchRes(txnId, true, entries, "OK");
    }

    public static LogFetchRes failure(long txnId, Throwable error) {
        return new LogFetchRes(txnId, true, List.of(), error.toString());
    }
}
