package io.masterkun.kactor.cluster.membership;

import com.google.protobuf.MessageLite;

import java.util.List;

public class MetaKeyBuilder {
    private int id;
    private String name;
    private String description;

    public MetaKeyBuilder id(int id) {
        this.id = id;
        return this;
    }

    public MetaKeyBuilder name(String name) {
        this.name = name;
        return this;
    }

    public MetaKeyBuilder description(String description) {
        this.description = description;
        return this;
    }

    public MetaKeys.ByteMetaKey build(byte defaultValue) {
        return new MetaKeys.ByteMetaKey(id, name, description, defaultValue);
    }

    public MetaKeys.ShortMetaKey build(short defaultValue) {
        return new MetaKeys.ShortMetaKey(id, name, description, defaultValue);
    }

    public MetaKeys.IntMetaKey build(int defaultValue) {
        return new MetaKeys.IntMetaKey(id, name, description, defaultValue);
    }

    public MetaKeys.LongMetaKey build(long defaultValue) {
        return new MetaKeys.LongMetaKey(id, name, description, defaultValue);
    }

    public MetaKeys.FloatMetaKey build(float defaultValue) {
        return new MetaKeys.FloatMetaKey(id, name, description, defaultValue);
    }

    public MetaKeys.DoubleMetaKey build(double defaultValue) {
        return new MetaKeys.DoubleMetaKey(id, name, description, defaultValue);
    }

    public MetaKeys.BooleanMetaKey build(boolean defaultValue) {
        return new MetaKeys.BooleanMetaKey(id, name, description, defaultValue);
    }

    public MetaKeys.StringMetaKey build(String defaultValue) {
        return new MetaKeys.StringMetaKey(id, name, description, defaultValue);
    }

    public MetaKeys.StringListMetaKey build(List<String> defaultValue) {
        return new MetaKeys.StringListMetaKey(id, name, description, defaultValue);
    }

    public <T extends MessageLite> MetaKeys.ProtobufMetaKey<T> build(T defaultValue) {
        return new MetaKeys.ProtobufMetaKey<>(id, name, description, defaultValue);
    }

    public <T extends Enum<T>> MetaKeys.EnumMetaKey<T> build(T defaultValue) {
        return new MetaKeys.EnumMetaKey<>(id, name, description, defaultValue);
    }
}
