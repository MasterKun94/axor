package io.axor.cp.messages;

import org.jetbrains.annotations.NotNull;

public record LogEntryId(long index, long term) implements Comparable<LogEntryId> {
    public static final LogEntryId INITIAL = new LogEntryId(0, 0);

    public LogEntryId next() {
        return new LogEntryId(index + 1, term);
    }

    @Override
    public int compareTo(@NotNull LogEntryId o) {
        int compare = Long.compare(term, o.term);
        return compare != 0 ? compare : Long.compare(index, o.index);
    }

    public boolean isAfter(LogEntryId id) {
        return index > id.index && term >= id.term;
    }

    public boolean isBefore(LogEntryId id) {
        return index < id.index && term <= id.term;
    }

    public boolean isNext(LogEntryId id) {
        return index + 1 == id.index && term >= id.term;
    }
}
