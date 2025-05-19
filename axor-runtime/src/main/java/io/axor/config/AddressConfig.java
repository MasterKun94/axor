package io.axor.config;

import io.axor.commons.config.ConfigField;

public record AddressConfig(
        @ConfigField(value = "host", nullable = true) String host,
        @ConfigField(value = "port", nullable = true) Integer port) {
}
