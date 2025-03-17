package io.masterkun.axor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.Serde;
import io.masterkun.axor.runtime.SerdeByteArrayInputStreamAdaptor;

import java.io.InputStream;

public class KryoAutoTypeSerde<T> implements Serde<T> {
    private final MsgType<T> msgType;
    private final KryoSerdeFactory factory;

    public KryoAutoTypeSerde(MsgType<T> msgType,
                             KryoSerdeFactory factory) {
        this.msgType = msgType;
        this.factory = factory;
    }

    @Override
    public InputStream serialize(T object) {
        return new SerdeByteArrayInputStreamAdaptor<>((stream, data) -> {
            var instance = factory.getKryoInstance();
            Kryo kryo = instance.getKryo();
            Output output = instance.getOutput();
            output.setOutputStream(stream);
            kryo.writeClassAndObject(output, object);
            output.flush();
            return (int) output.total();
        }, object);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T deserialize(InputStream stream) {
        var instance = factory.getKryoInstance();
        Kryo kryo = instance.getKryo();
        Input input = instance.getInput();
        input.setInputStream(stream);
        return (T) kryo.readClassAndObject(input);
    }

    @Override
    public MsgType<T> getType() {
        return msgType;
    }

    @Override
    public String getImpl() {
        return "kryo";
    }
}
