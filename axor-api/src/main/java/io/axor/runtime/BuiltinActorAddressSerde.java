package io.axor.runtime;

import io.axor.api.ActorAddress;
import io.axor.runtime.impl.BuiltinSerde;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class BuiltinActorAddressSerde implements BuiltinSerde<ActorAddress> {
    @Override
    public void doSerialize(ActorAddress obj, DataOutput out) throws IOException {
        out.writeUTF(obj.system());
        out.writeUTF(obj.host());
        out.writeShort(obj.port());
        out.writeUTF(obj.name());
    }

    @Override
    public ActorAddress doDeserialize(DataInput in) throws IOException {
        return ActorAddress.create(in.readUTF(), in.readUTF(), in.readUnsignedShort(),
                in.readUTF());
    }

    @Override
    public MsgType<ActorAddress> getType() {
        return MsgType.of(ActorAddress.class);
    }
}
