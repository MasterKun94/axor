package io.axor.runtime.serde.protobuf;

import com.google.protobuf.MessageLite;
import io.axor.runtime.AbstractSerdeFactory;
import io.axor.runtime.MsgType;
import io.axor.runtime.Serde;
import io.axor.runtime.SerdeRegistry;

public class ProtobufSerdeFactory extends AbstractSerdeFactory {
    public ProtobufSerdeFactory(SerdeRegistry registry) {
        super("protobuf", registry);
    }

    @Override
    public boolean support(MsgType<?> type) {
        return MessageLite.class.isAssignableFrom(type.type());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Serde<T> create(MsgType<T> type) {
        return (Serde<T>) new ProtobufSerde<>((MsgType<? extends MessageLite>) type);
    }
}
