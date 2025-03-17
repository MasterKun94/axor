package io.masterkun.axor.runtime.stream.grpc;

import io.grpc.Metadata;

import java.nio.ByteBuffer;

public class IntBinaryMarshaller implements Metadata.BinaryMarshaller<Integer> {
    @Override
    public byte[] toBytes(Integer value) {
        return ByteBuffer.allocate(4)
                .putInt(value)
                .array();
    }

    @Override
    public Integer parseBytes(byte[] serialized) {
        return ByteBuffer.wrap(serialized).getInt();
    }
}
