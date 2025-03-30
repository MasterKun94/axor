package io.axor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.exception.ActorException;
import io.axor.exception.ActorRuntimeException;
import io.axor.runtime.MsgType;

public class KryoActorSerializer extends Serializer<ActorRef> {
    private final ActorSystem system;
    private final Serializer<ActorAddress> addressSerializer;
    private final Serializer<MsgType> msgTypeSerializer;

    public KryoActorSerializer(ActorSystem system, Serializer<ActorAddress> addressSerializer,
                               Serializer<MsgType> msgTypeSerializer) {
        this.system = system;
        this.addressSerializer = addressSerializer;
        this.msgTypeSerializer = msgTypeSerializer;
    }

    @Override
    public void write(Kryo kryo, Output output, ActorRef object) {
        addressSerializer.write(kryo, output, object.address());
        msgTypeSerializer.write(kryo, output, object.msgType());
    }

    @Override
    public ActorRef read(Kryo kryo, Input input, Class<? extends ActorRef> type) {
        try {
            return system.get(
                    addressSerializer.read(kryo, input, ActorAddress.class),
                    msgTypeSerializer.read(kryo, input, MsgType.class)
            );
        } catch (ActorException e) {
            throw new ActorRuntimeException(e);
        }
    }
}
