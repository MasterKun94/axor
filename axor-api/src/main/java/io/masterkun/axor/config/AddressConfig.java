package io.masterkun.axor.config;

import io.masterkun.axor.commons.config.ConfigField;

public record AddressConfig(
        @ConfigField(value = "host", nullable = true) String host,
        @ConfigField(value = "port", nullable = true) Integer port) {
}
