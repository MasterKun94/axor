package io.axor.cp.messages;

import java.util.List;

public record LogAppend(long txnId,
                        List<LogEntry> entries,
                        LogEntryId currentCommited) implements LeaderMessage {
}
