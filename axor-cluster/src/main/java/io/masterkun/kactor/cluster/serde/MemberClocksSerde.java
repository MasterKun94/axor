package io.masterkun.kactor.cluster.serde;

import io.masterkun.kactor.cluster.membership.MemberClock;
import io.masterkun.kactor.cluster.membership.MemberClocks;
import io.masterkun.kactor.cluster.membership.Unsafe;
import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.impl.BuiltinSerde;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MemberClocksSerde implements BuiltinSerde<MemberClocks> {
    private final BuiltinSerde<MemberClock> memberEventSerde;

    public MemberClocksSerde(BuiltinSerde<MemberClock> memberEventSerde) {
        this.memberEventSerde = memberEventSerde;
    }

    @Override
    public void doSerialize(MemberClocks obj, DataOutput out) throws IOException {
        MemberClock[] unwrap = Unsafe.unwrap(obj);
        int length = unwrap.length;
        out.writeShort(length);
        if (length == 0) {
            return;
        }
        for (MemberClock memberEvent : unwrap) {
            memberEventSerde.doSerialize(memberEvent, out);
        }
    }

    @Override
    public MemberClocks doDeserialize(DataInput in) throws IOException {
        int length = in.readShort();
        if (length == 0) {
            return MemberClocks.EMPTY;
        }
        MemberClock[] unwrap = new MemberClock[length];
        for (int i = 0; i < length; i++) {
            unwrap[i] = memberEventSerde.doDeserialize(in);
        }
        return Unsafe.wrap(unwrap);
    }

    @Override
    public MsgType<MemberClocks> getType() {
        return MsgType.of(MemberClocks.class);
    }
}
