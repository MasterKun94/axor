package io.axor.cluster.membership;

import io.axor.commons.collection.IntObjectMap;

public class Unsafe {
    public static VectorClock wrapNoCheck(long... array) {
        return new VectorClock(array);
    }

    public static long[] unwrap(VectorClock vectorClock) {
        return vectorClock.array();
    }

    public static VectorClock inc(VectorClock vectorClock, long uid) {
        return vectorClock.unsafeInc(uid);
    }

    public static MemberEvents wrap(MemberEvent... events) {
        return new MemberEvents(events);
    }

    public static MemberClocks wrap(MemberClock... clocks) {
        return new MemberClocks(clocks);
    }

    public static MemberEvent[] unwrap(MemberEvents events) {
        return events.unwrap();
    }

    public static MemberClock[] unwrap(MemberClocks clocks) {
        return clocks.unwrap();
    }

    public static IntObjectMap<MetaInfo.BytesHolder> unwrap(MetaInfo metaInfo) {
        return metaInfo.unwrap();
    }
}
