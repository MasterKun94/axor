package io.axor.runtime.stream.grpc;

import io.axor.runtime.MsgType;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.StreamAddress;
import io.axor.runtime.StreamDefinition;
import io.grpc.Metadata;

class StreamDefinitionBinaryMarshaller implements Metadata.AsciiMarshaller<StreamDefinition<?>> {
    private final SerdeRegistry registry;

    StreamDefinitionBinaryMarshaller(SerdeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String toAsciiString(StreamDefinition<?> value) {
        MsgType<?> type = value.serde().getType();
        String typeName = type.qualifiedName();
        String string = value.address().toString();
        return string + "#" + value.serde().getImpl() + "#" + typeName;
    }

    @Override
    public StreamDefinition<?> parseAsciiString(String serialized) {
        String[] split = serialized.split("#", 3);
        if (split.length != 3) {
            throw new IllegalArgumentException("Invalid stream definition: " + serialized);
        }
        StreamAddress address = StreamAddress.fromString(split[0]);
        String impl = split[1];
        MsgType<?> msgType = MsgType.parse(split[2]);
        return new StreamDefinition<>(address, registry.create(impl, msgType));
    }
}
