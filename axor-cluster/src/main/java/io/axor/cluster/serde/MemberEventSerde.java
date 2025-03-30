package io.axor.cluster.serde;

import io.axor.cluster.membership.Member;
import io.axor.cluster.membership.MemberAction;
import io.axor.cluster.membership.MemberEvent;
import io.axor.cluster.membership.VectorClock;
import io.axor.runtime.MsgType;
import io.axor.runtime.impl.BuiltinSerde;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MemberEventSerde implements BuiltinSerde<MemberEvent> {
    private final BuiltinSerde<Member> memberSerde;
    private final BuiltinSerde<VectorClock> vectorClockSerde;

    public MemberEventSerde(BuiltinSerde<Member> memberSerde,
                            BuiltinSerde<VectorClock> vectorClockSerde) {
        this.memberSerde = memberSerde;
        this.vectorClockSerde = vectorClockSerde;
    }

    @Override
    public void doSerialize(MemberEvent obj, DataOutput out) throws IOException {
        memberSerde.doSerialize(obj.member(), out);
        out.writeShort(obj.action().ordinal());
        vectorClockSerde.doSerialize(obj.clock(), out);
    }

    @Override
    public MemberEvent doDeserialize(DataInput in) throws IOException {
        return new MemberEvent(
                memberSerde.doDeserialize(in),
                MemberAction.values()[in.readShort()],
                vectorClockSerde.doDeserialize(in));
    }

    @Override
    public MsgType<MemberEvent> getType() {
        return MsgType.of(MemberEvent.class);
    }
}
