package io.masterkun.axor.runtime.stream.grpc;

import com.google.protobuf.Message;
import io.masterkun.axor.runtime.Serde;
import io.masterkun.axor.runtime.serde.protobuf.ProtobufSerde;

public class DefaultMessageGetter {
    public Message get(Serde<?> serde) {
        return (Message) ((ProtobufSerde<?>) serde).getDefaultInstance();
    }
}
