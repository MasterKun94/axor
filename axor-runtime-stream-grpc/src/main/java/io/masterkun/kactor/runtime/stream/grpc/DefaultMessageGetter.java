package io.masterkun.kactor.runtime.stream.grpc;

import com.google.protobuf.Message;
import io.masterkun.kactor.runtime.Serde;
import io.masterkun.kactor.runtime.serde.protobuf.ProtobufSerde;

public class DefaultMessageGetter {
    public Message get(Serde<?> serde) {
        return (Message) ((ProtobufSerde<?>) serde).getDefaultInstance();
    }
}
