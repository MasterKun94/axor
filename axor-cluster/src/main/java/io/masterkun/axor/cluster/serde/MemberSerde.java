package io.masterkun.axor.cluster.serde;

import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorRefRich;
import io.masterkun.axor.cluster.membership.Member;
import io.masterkun.axor.cluster.membership.MetaInfo;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.impl.BuiltinSerde;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

@SuppressWarnings("rawtypes")
public class MemberSerde implements BuiltinSerde<Member> {
    private final BuiltinSerde<MetaInfo> metaInfoSerde;
    private final BuiltinSerde<ActorRef> actorRefSerde;

    public MemberSerde(BuiltinSerde<MetaInfo> metaInfoSerde,
                       BuiltinSerde<ActorRef> actorRefSerde) {
        this.metaInfoSerde = metaInfoSerde;
        this.actorRefSerde = actorRefSerde;
    }

    @Override
    public void doSerialize(Member obj, DataOutput out) throws IOException {
        out.writeLong(obj.uid());
        metaInfoSerde.doSerialize(obj.metaInfo(), out);
        actorRefSerde.doSerialize(obj.actor(), out);
    }

    @Override
    public Member doDeserialize(DataInput in) throws IOException {
        return new Member(in.readLong(),
                metaInfoSerde.doDeserialize(in),
                ((ActorRefRich<?>) actorRefSerde.doDeserialize(in)).unsafeCast());
    }

    @Override
    public MsgType<Member> getType() {
        return MsgType.of(Member.class);
    }
}
