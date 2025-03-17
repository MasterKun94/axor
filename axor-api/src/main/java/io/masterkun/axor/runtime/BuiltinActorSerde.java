package io.masterkun.axor.runtime;

import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.exception.ActorException;
import io.masterkun.axor.exception.ActorIOException;
import io.masterkun.axor.runtime.impl.BuiltinSerde;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

@SuppressWarnings({"rawtypes", "unchecked"})
public class BuiltinActorSerde implements BuiltinSerde<ActorRef> {
    private final BuiltinSerde<ActorAddress> addressSerde;
    private final BuiltinSerde<MsgType> msgTypeSerde;
    private final ActorSystem system;

    public BuiltinActorSerde(BuiltinSerde<ActorAddress> addressSerde,
                             BuiltinSerde<MsgType> msgTypeSerde,
                             ActorSystem system) {
        this.addressSerde = addressSerde;
        this.msgTypeSerde = msgTypeSerde;
        this.system = system;
    }

    @Override
    public void doSerialize(ActorRef obj, DataOutput out) throws IOException {
        addressSerde.doSerialize(obj.address(), out);
        msgTypeSerde.doSerialize(obj.msgType(), out);
    }

    @Override
    public ActorRef doDeserialize(DataInput in) throws IOException {
        try {
            return system.get(addressSerde.doDeserialize(in), msgTypeSerde.doDeserialize(in));
        } catch (ActorException e) {
            throw new ActorIOException(e);
        }
    }

    @Override
    public MsgType<ActorRef> getType() {
        return MsgType.of(ActorRef.class);
    }
}
