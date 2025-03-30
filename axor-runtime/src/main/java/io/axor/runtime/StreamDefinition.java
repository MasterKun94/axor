package io.axor.runtime;

import java.util.Objects;

public record StreamDefinition<T>(StreamAddress address, Serde<T> serde) {
    @Override
    public String toString() {
        return "StringDefinition[address=" + address + ", serde=" + Serde.toString(serde) + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        StreamDefinition<?> that = (StreamDefinition<?>) o;
        return Objects.equals(serde.getImpl(), that.serde.getImpl()) &&
               Objects.equals(serde.getType(), that.serde.getType()) &&
               Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, serde.getImpl(), serde.getType());
    }
}
