package io.masterkun.kactor.runtime.serde.protobuf;

import com.google.protobuf.MessageLite;
import io.masterkun.kactor.runtime.AbstractSerdeFactory;
import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.Serde;
import io.masterkun.kactor.runtime.SerdeRegistry;

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
