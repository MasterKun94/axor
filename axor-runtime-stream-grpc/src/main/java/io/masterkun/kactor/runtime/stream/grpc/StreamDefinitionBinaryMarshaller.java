package io.masterkun.kactor.runtime.stream.grpc;

import io.grpc.Metadata;
import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.SerdeRegistry;
import io.masterkun.kactor.runtime.StreamAddress;
import io.masterkun.kactor.runtime.StreamDefinition;

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
