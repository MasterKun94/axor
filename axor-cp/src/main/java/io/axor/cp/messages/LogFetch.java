package io.axor.cp.messages;

public record LogFetch(long txnId, LogEntryId start, int maxEntryLimit,
                       int maxSizeLimit) implements ServiceMessage {
}
