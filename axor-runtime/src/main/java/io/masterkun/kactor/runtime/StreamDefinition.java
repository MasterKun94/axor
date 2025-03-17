package io.masterkun.kactor.runtime;

public record StreamDefinition<T>(StreamAddress address, Serde<T> serde) {
    @Override
    public String toString() {
        return "StringDefinition[address=" + address + ", serde=" + Serde.toString(serde) + "]";
    }
}
