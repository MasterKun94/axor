package io.axor.raft;

import org.jetbrains.annotations.NotNull;

public record LogId(long index, long term) implements Comparable<LogId> {
    public static final LogId INITIAL = new LogId(0, 0);

    @Override
    public int compareTo(@NotNull LogId o) {
        int compare = Long.compare(term, o.term);
        return compare != 0 ? compare : Long.compare(index, o.index);
    }
}
