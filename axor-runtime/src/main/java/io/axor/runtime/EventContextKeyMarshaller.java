package io.axor.runtime;

import io.axor.commons.util.ByteArray;

import java.nio.charset.StandardCharsets;

public class EventContextKeyMarshaller {

    public static final EventContext.KeyMarshaller<Integer> INT =
            new EventContext.KeyMarshaller<>() {
        @Override
        public Integer read(byte[] bytes, int off, int len) {
            return ByteArray.getInt(bytes, off);
        }

        @Override
        public byte[] write(Integer value) {
            byte[] bytes = new byte[8];
            ByteArray.setInt(bytes, 0, value);
            return bytes;
        }
    };

    public static final EventContext.KeyMarshaller<Long> LONG = new EventContext.KeyMarshaller<>() {
        @Override
        public Long read(byte[] bytes, int off, int len) {
            return ByteArray.getLong(bytes, off);
        }

        @Override
        public byte[] write(Long value) {
            byte[] bytes = new byte[8];
            ByteArray.setLong(bytes, 0, value);
            return bytes;
        }
    };

    public static final EventContext.KeyMarshaller<String> STRING =
            new EventContext.KeyMarshaller<>() {
        @Override
        public String read(byte[] bytes, int off, int len) {
            return new String(bytes, off, len, StandardCharsets.UTF_8);
        }

        @Override
        public byte[] write(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    };
}
