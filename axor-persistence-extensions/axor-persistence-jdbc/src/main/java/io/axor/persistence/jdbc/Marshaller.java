package io.axor.persistence.jdbc;

public interface Marshaller<T> {
    byte[] toBytes(T t);

    T fromBytes(byte[] bytes);
}
