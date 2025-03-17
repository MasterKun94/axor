package io.masterkun.axor.cluster.membership;

import java.util.Collection;

public sealed interface Gossip extends MembershipMessage {
    static Gossip of(Collection<MemberEvent> members, long sender, boolean pull) {
        return new PushedEvents(Unsafe.wrap(members.toArray(MemberEvent[]::new)), sender, pull);
    }

    static Gossip of(Collection<MemberEvent> members, long sender) {
        return of(members, sender, false);
    }

    static Gossip of(MemberEvents events, long sender, boolean pull) {
        return new PushedEvents(events, sender, pull);
    }

    static Gossip of(MemberEvents events, long sender) {
        return of(events, sender, false);
    }

    static Gossip of(MemberEvent event, long sender, boolean pull) {
        return new PushedEvents(new MemberEvents(event), sender, pull);
    }

    static Gossip of(MemberEvent event, long sender) {
        return of(event, sender, false);
    }

    static Gossip ping(long sender, MemberClock... clocks) {
        return new Ping(sender, new MemberClocks(clocks));
    }

    static Gossip ping(long sender, MemberClocks clocks) {
        return new Ping(sender, clocks);
    }

    static Gossip pong(long sender) {
        return new Pong(sender, false);
    }

    static Gossip pong(long sender, boolean pull) {
        return new Pong(sender, pull);
    }

    MemberEvents events();

    long sender();

    boolean ping();

    boolean pull();

    MemberClocks clocks();

    record Ping(long sender, MemberClocks clocks) implements Gossip {
        @Override
        public MemberEvents events() {
            return MemberEvents.EMPTY;
        }

        @Override
        public boolean ping() {
            return true;
        }

        @Override
        public boolean pull() {
            return false;
        }

        @Override
        public String toString() {
            return "Ping[" +
                    "sender=" + sender +
                    ", clocks=" + clocks +
                    ']';
        }
    }

    record Pong(long sender, boolean pull) implements Gossip {
        @Override
        public MemberEvents events() {
            return MemberEvents.EMPTY;
        }

        @Override
        public boolean ping() {
            return false;
        }

        @Override
        public MemberClocks clocks() {
            return MemberClocks.EMPTY;
        }

        @Override
        public String toString() {
            return "Pong[" +
                    "sender=" + sender +
                    ", pull=" + pull +
                    ']';
        }
    }

    record PushedEvents(MemberEvents events, long sender, boolean pull) implements Gossip {

        @Override
        public MemberClocks clocks() {
            return MemberClocks.EMPTY;
        }

        @Override
        public boolean ping() {
            return false;
        }

        @Override
        public String toString() {
            return "Gossip[events=" + events +
                    ", sender=" + sender +
                    ", pull=" + pull +
                    ']';
        }
    }
}
