package io.masterkun.kactor.cluster.serde;

import io.masterkun.kactor.cluster.membership.MemberEvent;
import io.masterkun.kactor.cluster.membership.MemberEvents;
import io.masterkun.kactor.cluster.membership.Unsafe;
import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.impl.BuiltinSerde;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MemberEventsSerde implements BuiltinSerde<MemberEvents> {
    private final BuiltinSerde<MemberEvent> memberEventSerde;

    public MemberEventsSerde(BuiltinSerde<MemberEvent> memberEventSerde) {
        this.memberEventSerde = memberEventSerde;
    }

    @Override
    public void doSerialize(MemberEvents obj, DataOutput out) throws IOException {
        MemberEvent[] unwrap = Unsafe.unwrap(obj);
        int length = unwrap.length;
        out.writeShort(length);
        if (length == 0) {
            return;
        }
        for (MemberEvent memberEvent : unwrap) {
            memberEventSerde.doSerialize(memberEvent, out);
        }
    }

    @Override
    public MemberEvents doDeserialize(DataInput in) throws IOException {
        int length = in.readShort();
        if (length == 0) {
            return MemberEvents.EMPTY;
        }
        MemberEvent[] unwrap = new MemberEvent[length];
        for (int i = 0; i < length; i++) {
            unwrap[i] = memberEventSerde.doDeserialize(in);
        }
        return Unsafe.wrap(unwrap);
    }

    @Override
    public MsgType<MemberEvents> getType() {
        return MsgType.of(MemberEvents.class);
    }
}
