package io.axor.runtime.stream.grpc;

import com.google.protobuf.Message;
import io.axor.runtime.Serde;
import io.axor.runtime.serde.protobuf.ProtobufSerde;

public class DefaultMessageGetter {
    public Message get(Serde<?> serde) {
        return (Message) ((ProtobufSerde<?>) serde).getDefaultInstance();
    }
}
