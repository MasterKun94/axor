package io.masterkun.axor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.masterkun.axor.exception.ActorIOException;
import io.masterkun.axor.exception.ActorRuntimeException;
import io.masterkun.axor.runtime.impl.BuiltinSerde;

import java.io.DataOutputStream;
import java.io.IOException;

class SerializerAdaptor<T> extends Serializer<T> {
    private final BuiltinSerde<T> serde;

    SerializerAdaptor(BuiltinSerde<T> serde) {
        this.serde = serde;
    }

    @Override
    public void write(Kryo kryo, Output output, T object) {
        try {
            var dout = new DataOutputStream(output);
            serde.doSerialize(object, dout);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public T read(Kryo kryo, Input input, Class<? extends T> type) {
        try {
            return serde.deserialize(input);
        } catch (ActorIOException e) {
            throw new ActorRuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
