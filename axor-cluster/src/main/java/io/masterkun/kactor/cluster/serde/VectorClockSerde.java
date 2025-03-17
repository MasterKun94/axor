package io.masterkun.kactor.cluster.serde;

import io.masterkun.kactor.cluster.membership.Unsafe;
import io.masterkun.kactor.cluster.membership.VectorClock;
import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.impl.BuiltinSerde;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class VectorClockSerde implements BuiltinSerde<VectorClock> {
    @Override
    public void doSerialize(VectorClock obj, DataOutput out) throws IOException {
        long[] unwrap = Unsafe.unwrap(obj);
        out.writeShort(unwrap.length);
        for (long l : unwrap) {
            out.writeLong(l);
        }
    }

    @Override
    public VectorClock doDeserialize(DataInput in) throws IOException {
        int size = in.readShort();
        long[] unwrap = new long[size];
        for (int i = 0; i < size; i++) {
            unwrap[i] = in.readLong();
        }
        return Unsafe.wrapNoCheck(unwrap);
    }

    @Override
    public MsgType<VectorClock> getType() {
        return MsgType.of(VectorClock.class);
    }
}
