package io.axor.cluster.serde;

import io.axor.cluster.membership.MemberClock;
import io.axor.cluster.membership.VectorClock;
import io.axor.runtime.MsgType;
import io.axor.runtime.impl.BuiltinSerde;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MemberClockSerde implements BuiltinSerde<MemberClock> {
    private final BuiltinSerde<VectorClock> vectorClockSerde = new VectorClockSerde();

    @Override
    public void doSerialize(MemberClock obj, DataOutput out) throws IOException {
        out.writeLong(obj.uid());
        vectorClockSerde.doSerialize(obj.clock(), out);
    }

    @Override
    public MemberClock doDeserialize(DataInput in) throws IOException {
        return new MemberClock(in.readLong(), vectorClockSerde.doDeserialize(in));
    }

    @Override
    public MsgType<MemberClock> getType() {
        return MsgType.of(MemberClock.class);
    }
}
