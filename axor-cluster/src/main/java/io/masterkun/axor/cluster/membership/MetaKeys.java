package io.masterkun.axor.cluster.membership;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import io.masterkun.axor.cluster.internal.ByteArray;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class MetaKeys {
    private static final Map<Integer, String> CACHE = new ConcurrentHashMap<>();
    private static final boolean ID_CHECK_ENABLED = Boolean.parseBoolean(System.getProperty(
            "axor.cluster.meta-key.id-check", "true"));

    public static BooleanMetaKey create(int id, String name, String description,
                                        boolean defaultValue) {
        return new BooleanMetaKey(id, name, description, defaultValue);
    }

    public static ByteMetaKey create(int id, String name, String description, byte defaultValue) {
        return new ByteMetaKey(id, name, description, defaultValue);
    }

    public static ShortMetaKey create(int id, String name, String description, short defaultValue) {
        return new ShortMetaKey(id, name, description, defaultValue);
    }

    public static IntMetaKey create(int id, String name, String description, int defaultValue) {
        return new IntMetaKey(id, name, description, defaultValue);
    }

    public static LongMetaKey create(int id, String name, String description, long defaultValue) {
        return new LongMetaKey(id, name, description, defaultValue);
    }

    public static FloatMetaKey create(int id, String name, String description, float defaultValue) {
        return new FloatMetaKey(id, name, description, defaultValue);
    }

    public static DoubleMetaKey create(int id, String name, String description,
                                       double defaultValue) {
        return new DoubleMetaKey(id, name, description, defaultValue);
    }

    public static StringMetaKey create(int id, String name, String description,
                                       String defaultValue) {
        return new StringMetaKey(id, name, description, defaultValue);
    }

    public static StringListMetaKey create(int id, String name, String description,
                                           List<String> defaultValue) {
        return new StringListMetaKey(id, name, description, List.copyOf(defaultValue));
    }

    public static <T extends Enum<T>> EnumMetaKey<T> create(int id, String name,
                                                            String description, T defaultValue) {
        return new EnumMetaKey<>(id, name, description, defaultValue);
    }

    public static <T extends MessageLite> ProtobufMetaKey<T> create(int id, String name,
                                                                    String description,
                                                                    T defaultValue) {
        return new ProtobufMetaKey<>(id, name, description, defaultValue);
    }

    interface UpdateFunc {
        byte[] apply(byte[] bytes, int offset, int length);
    }

    record Upsert(int id, byte[] bytes) implements MetaKey.Action {
    }

    record Update(int id, UpdateFunc func) implements MetaKey.Action {
    }

    record Delete(int id) implements MetaKey.Action {
    }

    static sealed class AbstractMetaKey<T> implements MetaKey<T> {
        private final int id;
        private final String name;
        private final String description;
        private final MetaSerde<T> serde;
        private final MetaInfo.BytesHolder defaultValue;

        AbstractMetaKey(int id, String name, String description, T defaultValue,
                        MetaSerde<T> serde) {
            if (ID_CHECK_ENABLED) {
                String used;
                if ((used = CACHE.putIfAbsent(id, name)) != null) {
                    throw new IllegalArgumentException("duplicate meta key " + name + ": " + id +
                                                       ", already used by: " + used);
                }
            }
            this.id = id;
            this.name = name;
            this.description = description;
            this.serde = serde;
            this.defaultValue = new MetaInfo.BytesHolder(serde.serialize(defaultValue));
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public boolean metaEquals(MetaInfo left, MetaInfo right) {
            var leftHolder = Unsafe.unwrap(left).getOrDefault(id, defaultValue);
            var rightHolder = Unsafe.unwrap(right).getOrDefault(id, defaultValue);
            return leftHolder.equals(rightHolder);
        }

        @Override
        public boolean contains(MetaInfo metaInfo, T value) {
            return get(metaInfo).equals(value);
        }

        protected MetaInfo.BytesHolder getHolder(MetaInfo metaInfo) {
            return metaInfo.unwrap().getOrDefault(id, defaultValue);
        }

        @Override
        public T get(MetaInfo metaInfo) {
            var holder = getHolder(metaInfo);
            return serde.deserialize(holder.bytes(), holder.offset(), holder.length());
        }

        @Override
        public Action upsert(T value) {
            return new Upsert(id, serde.serialize(value));
        }

        @Override
        public Action update(Function<T, T> updateFunc) {
            return new Update(id, (b, off, len) -> {
                if (b == null) {
                    b = defaultValue.bytes();
                    off = defaultValue.offset();
                    len = defaultValue.length();
                }
                return serde.serialize(updateFunc.apply(serde.deserialize(b, off, len)));
            });
        }

        @Override
        public Action delete() {
            return new Delete(id);
        }
    }

    public static final class BooleanMetaKey extends AbstractMetaKey<Boolean> {

        BooleanMetaKey(int id, String name, String description, Boolean defaultValue) {
            super(id, name, description, defaultValue, new MetaSerde<>() {
                @Override
                public byte[] serialize(Boolean b) {
                    byte[] bytes = new byte[1];
                    ByteArray.setBoolean(bytes, 0, b);
                    return bytes;
                }

                @Override
                public Boolean deserialize(byte[] bytes, int offset, int length) {
                    return ByteArray.getBoolean(bytes, offset);
                }
            });
        }

        public boolean getValue(MetaInfo metaInfo) {
            MetaInfo.BytesHolder holder = getHolder(metaInfo);
            return ByteArray.getBoolean(holder.bytes(), holder.offset());
        }
    }

    public static final class ByteMetaKey extends AbstractMetaKey<Byte> {

        ByteMetaKey(int id, String name, String description, Byte defaultValue) {
            super(id, name, description, defaultValue, new MetaSerde<>() {
                @Override
                public byte[] serialize(Byte b) {
                    return new byte[]{b};
                }

                @Override
                public Byte deserialize(byte[] bytes, int offset, int length) {
                    return bytes[offset];
                }
            });
        }

        public byte getValue(MetaInfo metaInfo) {
            MetaInfo.BytesHolder holder = getHolder(metaInfo);
            return holder.bytes()[holder.offset()];
        }
    }

    public static final class ShortMetaKey extends AbstractMetaKey<Short> {
        ShortMetaKey(int id, String name, String description, Short defaultValue) {
            super(id, name, description, defaultValue, new MetaSerde<>() {

                @Override
                public byte[] serialize(Short object) {
                    byte[] bytes = new byte[2];
                    ByteArray.setShort(bytes, 0, object);
                    return bytes;
                }

                @Override
                public Short deserialize(byte[] bytes, int offset, int length) {
                    return ByteArray.getShort(bytes, offset);
                }
            });
        }

        public short getValue(MetaInfo metaInfo) {
            MetaInfo.BytesHolder holder = getHolder(metaInfo);
            return ByteArray.getShort(holder.bytes(), holder.offset());
        }
    }

    public static final class IntMetaKey extends AbstractMetaKey<Integer> {
        IntMetaKey(int id, String name, String description, Integer defaultValue) {
            super(id, name, description, defaultValue, new MetaSerde<>() {

                @Override
                public byte[] serialize(Integer object) {
                    byte[] bytes = new byte[4];
                    ByteArray.setInt(bytes, 0, object);
                    return bytes;
                }

                @Override
                public Integer deserialize(byte[] bytes, int offset, int length) {
                    return ByteArray.getInt(bytes, offset);
                }
            });
        }

        public int getValue(MetaInfo metaInfo) {
            MetaInfo.BytesHolder holder = getHolder(metaInfo);
            return ByteArray.getInt(holder.bytes(), holder.offset());
        }
    }

    public static final class LongMetaKey extends AbstractMetaKey<Long> {
        LongMetaKey(int id, String name, String description, Long defaultValue) {
            super(id, name, description, defaultValue, new MetaSerde<>() {
                @Override
                public byte[] serialize(Long object) {
                    byte[] bytes = new byte[8];
                    ByteArray.setLong(bytes, 0, object);
                    return bytes;
                }

                @Override
                public Long deserialize(byte[] bytes, int offset, int length) {
                    return ByteArray.getLong(bytes, offset);
                }
            });
        }

        public long getValue(MetaInfo metaInfo) {
            MetaInfo.BytesHolder holder = getHolder(metaInfo);
            return ByteArray.getLong(holder.bytes(), holder.offset());
        }
    }

    public static final class FloatMetaKey extends AbstractMetaKey<Float> {
        FloatMetaKey(int id, String name, String description, Float defaultValue) {
            super(id, name, description, defaultValue, new MetaSerde<>() {

                @Override
                public byte[] serialize(Float object) {
                    byte[] bytes = new byte[4];
                    ByteArray.setFloat(bytes, 0, object);
                    return bytes;
                }

                @Override
                public Float deserialize(byte[] bytes, int offset, int length) {
                    return ByteArray.getFloat(bytes, offset);
                }
            });
        }

        public float getValue(MetaInfo metaInfo) {
            MetaInfo.BytesHolder holder = getHolder(metaInfo);
            return ByteArray.getFloat(holder.bytes(), holder.offset());
        }
    }

    public static final class DoubleMetaKey extends AbstractMetaKey<Double> {
        DoubleMetaKey(int id, String name, String description, Double defaultValue) {
            super(id, name, description, defaultValue, new MetaSerde<>() {
                @Override
                public byte[] serialize(Double object) {
                    byte[] bytes = new byte[8];
                    ByteArray.setDouble(bytes, 0, object);
                    return bytes;
                }

                @Override
                public Double deserialize(byte[] bytes, int offset, int length) {
                    return ByteArray.getDouble(bytes, offset);
                }
            });
        }

        public double getValue(MetaInfo metaInfo) {
            MetaInfo.BytesHolder holder = getHolder(metaInfo);
            return ByteArray.getDouble(holder.bytes(), holder.offset());
        }
    }

    public static final class StringMetaKey extends AbstractMetaKey<String> {
        StringMetaKey(int id, String name, String description, String defaultValue) {
            super(id, name, description, defaultValue, new MetaSerde<>() {

                @Override
                public byte[] serialize(String object) {
                    return object.getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public String deserialize(byte[] bytes, int offset, int length) {
                    return new String(bytes, offset, length, StandardCharsets.UTF_8);
                }
            });
        }
    }

    public static final class StringListMetaKey extends AbstractMetaKey<List<String>> {
        StringListMetaKey(int id, String name, String description, List<String> defaultValue) {
            super(id, name, description, defaultValue, new MetaSerde<>() {
                @Override
                public byte[] serialize(List<String> object) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(bos);
                    try {
                        dos.writeShort(object.size());
                        for (String string : object) {
                            dos.writeUTF(string);
                        }
                        dos.flush();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return bos.toByteArray();
                }

                @Override
                public List<String> deserialize(byte[] bytes, int offset, int length) {
                    ByteArrayInputStream bis = new ByteArrayInputStream(bytes, offset, length);
                    DataInputStream dis = new DataInputStream(bis);
                    try {
                        short size = dis.readShort();
                        if (size == 0) {
                            return Collections.emptyList();
                        }
                        List<String> result = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            result.add(dis.readUTF());
                        }
                        return Collections.unmodifiableList(result);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    public static final class ProtobufMetaKey<T extends MessageLite> extends AbstractMetaKey<T> {
        ProtobufMetaKey(int id, String name, String description, T defaultValue) {
            super(id, name, description, defaultValue, new MetaSerde<>() {
                private final Parser<T> parser = (Parser<T>) defaultValue.getParserForType();

                @Override
                public byte[] serialize(T object) {
                    return object.toByteArray();
                }

                @Override
                public T deserialize(byte[] bytes, int offset, int length) {
                    try {
                        return parser.parseFrom(bytes, offset, length);
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    public static final class EnumMetaKey<T extends Enum<T>> extends AbstractMetaKey<T> {
        EnumMetaKey(int id, String name, String description, T defaultValue) {
            super(id, name, description, defaultValue, new MetaSerde<>() {
                private final T[] elems = defaultValue.getDeclaringClass().getEnumConstants();

                @Override
                public byte[] serialize(T object) {
                    byte[] bytes = new byte[2];
                    ByteArray.setShort(bytes, 0, (short) object.ordinal());
                    return bytes;
                }

                @Override
                public T deserialize(byte[] bytes, int offset, int length) {
                    return elems[ByteArray.getShort(bytes, offset)];
                }
            });
        }
    }
}
