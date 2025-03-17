package io.masterkun.axor.cluster.serde;

import io.masterkun.axor.cluster.membership.MemberEvent;
import io.masterkun.axor.cluster.membership.MemberEvents;
import io.masterkun.axor.cluster.membership.Unsafe;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.impl.BuiltinSerde;

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
