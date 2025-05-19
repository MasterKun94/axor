package io.axor.config;

import io.axor.api.Address;
import io.axor.commons.config.ConfigField;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public record NetworkConfig(
        @ConfigField("bind") AddressConfig bind,
        @ConfigField(value = "publish", nullable = true) AddressConfig publish) {

    public Address bindAddress() {
        return new Address(bind.host(), bind.port());
    }

    public Address publishAddress() {
        String host = null;
        Integer port = null;
        if (publish != null) {
            host = publish.host();
            port = publish.port();
        }
        if (host == null) {
            try {
                InetAddress parsed = InetAddress.getByName(bind.host());
                if (parsed.isAnyLocalAddress()) {
                    host = InetAddress.getLocalHost().getHostAddress();
                } else {
                    host = Objects.requireNonNull(bind.host());
                }
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }
        if (port == null) {
            port = Objects.requireNonNull(bind.port());
        }
        return new Address(host, port);
    }
}
