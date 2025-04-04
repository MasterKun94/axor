package io.axor.persistence.jdbc;

import io.axor.runtime.MsgType;
import io.axor.runtime.Serde;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class NoopSerde implements Serde<String> {
    @Override
    public InputStream serialize(String object) throws IOException {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public String deserialize(InputStream stream) throws IOException {
        return "";
    }

    @Override
    public MsgType<String> getType() {
        return MsgType.of(String.class);
    }

    @Override
    public String getImpl() {
        return "jdbc-noop";
    }
}
