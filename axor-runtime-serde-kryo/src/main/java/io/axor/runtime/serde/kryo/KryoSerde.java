package io.axor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.axor.runtime.MsgType;
import io.axor.runtime.Serde;
import io.axor.runtime.SerdeByteArrayInputStreamAdaptor;

import java.io.InputStream;
import java.util.Objects;

public class KryoSerde<T> implements Serde<T> {
    private final MsgType<T> msgType;
    private final KryoSerdeFactory factory;
    private int id = -1;

    public KryoSerde(MsgType<T> msgType,
                     KryoSerdeFactory factory) {
        this.msgType = msgType;
        this.factory = factory;
    }

    @SuppressWarnings("unchecked")
    private Serializer<T> getSerializer(Kryo kryo) {
        if (id == -1) {
            Registration registration = kryo.getRegistration(msgType.type());
            id = registration.getId();
            return registration.getSerializer();
        } else {
            return kryo.getRegistration(id).getSerializer();
        }
    }

    @Override
    public InputStream serialize(T object) {
        return new SerdeByteArrayInputStreamAdaptor<>((stream, data) -> {
            var instance = factory.getKryoInstance();
            Kryo kryo = instance.getKryo();
            Output output = instance.getOutput();
            output.setOutputStream(stream);
            kryo.writeObject(output, object, getSerializer(kryo));
            output.flush();
            return (int) output.total();
        }, object);
    }

    @Override
    public T deserialize(InputStream stream) {
        var instance = factory.getKryoInstance();
        Kryo kryo = instance.getKryo();
        Input input = instance.getInput();
        input.setInputStream(stream);
        return kryo.readObject(input, msgType.type(), getSerializer(kryo));
    }

    @Override
    public MsgType<T> getType() {
        return msgType;
    }

    @Override
    public String getImpl() {
        return "kryo";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        KryoSerde<?> kryoSerde = (KryoSerde<?>) o;
        return id == kryoSerde.id && Objects.equals(msgType, kryoSerde.msgType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msgType, id);
    }
}
