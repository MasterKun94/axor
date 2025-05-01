package io.axor.cp.messages;

import io.axor.api.ActorAddress;
import org.jetbrains.annotations.NotNull;

public record Peer(int id, int priority, ActorAddress address) implements Comparable<Peer> {
    @Override
    public int compareTo(@NotNull Peer o) {
        int compare = Integer.compare(priority, o.priority);
        return compare != 0 ? compare : Integer.compare(id, o.id);
    }
}
