package io.masterkun.kactor.runtime.stream.grpc;

import io.grpc.Metadata;

public class StringMarshaller implements Metadata.AsciiMarshaller<String> {

    @Override
    public String toAsciiString(String value) {
        return value;
    }

    @Override
    public String parseAsciiString(String serialized) {
        return serialized;
    }
}
