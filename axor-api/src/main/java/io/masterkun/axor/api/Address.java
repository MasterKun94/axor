package io.masterkun.axor.api;

public record Address(String host, int port) {
    @Override
    public String toString() {
        return host + ":" + port;
    }
}
