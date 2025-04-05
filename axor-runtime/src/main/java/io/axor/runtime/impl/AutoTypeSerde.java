package io.axor.runtime.impl;

import com.google.common.io.ByteStreams;
import io.axor.runtime.MsgType;
import io.axor.runtime.Serde;
import io.axor.runtime.SerdeRegistry;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@SuppressWarnings("rawtypes")
public class AutoTypeSerde<T> implements BuiltinSerde<T> {
    private final SerdeRegistry registry;
    private final BuiltinSerde<MsgType> msgTypeSerde;
    private final MsgType<T> msgType;

    public AutoTypeSerde(SerdeRegistry registry, MsgType<T> msgType) {
        this.registry = registry;
        this.msgTypeSerde = registry.getFactory(BuiltinSerdeFactory.class)
                .create(MsgType.of(MsgType.class));
        this.msgType = msgType;
    }

    @Override
    public void doSerialize(T obj, DataOutput out) throws IOException {
        MsgType<T> type = MsgType.of((Class<T>) obj.getClass());
        msgTypeSerde.doSerialize(type, out);
        Serde<T> serde = registry.create(type);
        if (serde instanceof BuiltinSerde<T> bSerde) {
            bSerde.doSerialize(obj, out);
        } else {
            try (InputStream in = serde.serialize(obj)) {
                if (in instanceof Drainable drainable) {
                    drainable.drainTo((OutputStream) out);
                } else {
                    ByteStreams.copy(in, (OutputStream) out);
                }
            }
        }
    }

    @Override
    public T doDeserialize(DataInput in) throws IOException {
        MsgType<T> type = msgTypeSerde.doDeserialize(in);
        Serde<T> serde = registry.create(type);
        if (serde instanceof BuiltinSerde<T> bSerde) {
            return bSerde.doDeserialize(in);
        } else {
            return serde.deserialize((InputStream) in);
        }
    }

    @Override
    public MsgType<T> getType() {
        return msgType;
    }
}
