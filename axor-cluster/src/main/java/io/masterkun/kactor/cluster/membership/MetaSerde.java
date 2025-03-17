package io.masterkun.kactor.cluster.membership;

public interface MetaSerde<T> {
    byte[] serialize(T object);

    T deserialize(byte[] bytes, int offset, int length);
}
