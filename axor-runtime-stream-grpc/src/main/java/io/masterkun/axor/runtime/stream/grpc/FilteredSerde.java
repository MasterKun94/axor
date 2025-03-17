package io.masterkun.axor.runtime.stream.grpc;

import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.Serde;

import java.io.IOException;
import java.io.InputStream;

public class FilteredSerde implements Serde<InputStream> {

    @Override
    public InputStream serialize(InputStream object) throws IOException {
        return null;
    }

    @Override
    public InputStream deserialize(InputStream stream) throws IOException {
        return null;
    }

    @Override
    public MsgType<InputStream> getType() {
        return null;
    }

    @Override
    public String getImpl() {
        return "";
    }
}
