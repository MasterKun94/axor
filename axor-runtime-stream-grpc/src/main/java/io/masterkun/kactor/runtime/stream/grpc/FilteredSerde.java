package io.masterkun.kactor.runtime.stream.grpc;

import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.Serde;

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
