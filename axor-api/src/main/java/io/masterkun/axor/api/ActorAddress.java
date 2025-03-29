package io.masterkun.axor.api;

import io.masterkun.axor.runtime.StreamAddress;

import java.net.URI;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ActorAddress {
    private static final Pattern PATTERN = Pattern.compile("^[_a-zA-Z][_/a-zA-Z0-9]*$");
    private final StreamAddress streamAddress;

    private ActorAddress(StreamAddress streamAddress) {
        this.streamAddress = streamAddress;
        validateString(system(), "system");
        validateString(name(), "name");
    }

    public static ActorAddress create(String string) {
        return create(StreamAddress.fromString(string));
    }

    public static ActorAddress create(String system, Address address, String name) {
        return create(system, address.host(), address.port(), name);
    }

    public static ActorAddress create(String system, String host, int port, String name) {
        return create(StreamAddress.fromComponents(system, host, port, name));
    }

    public static ActorAddress create(StreamAddress address) {
        return new ActorAddress(address);
    }

    public static ActorAddress create(URI uri,
                                      String defaultSystem,
                                      int defaultPort,
                                      String defaultName) {
        return create(StreamAddress.fromURI(uri, defaultPort, defaultSystem, defaultName));
    }

    private static void validateString(String string, String name) {
        Objects.requireNonNull(string, name);
        if (!PATTERN.matcher(string).matches()) {
            throw new IllegalArgumentException(string + " " + name + " not match pattern " + PATTERN.pattern());
        }
    }

    public String host() {
        return streamAddress.host();
    }

    public int port() {
        return streamAddress.port();
    }

    public Address address() {
        return new Address(host(), port());
    }

    public String system() {
        return streamAddress.service();
    }

    public String name() {
        return streamAddress.method();
    }

    @Override
    public String toString() {
        return streamAddress.toString();
    }

    public StreamAddress streamAddress() {
        return streamAddress;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ActorAddress) obj;
        return Objects.equals(this.streamAddress, that.streamAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(streamAddress);
    }
}
