package io.axor.runtime;

import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.exception.ActorException;
import io.axor.exception.ActorIOException;
import io.axor.runtime.impl.BuiltinSerde;

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
