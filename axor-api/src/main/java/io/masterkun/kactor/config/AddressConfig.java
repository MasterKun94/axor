package io.masterkun.kactor.config;

import io.masterkun.kactor.commons.config.ConfigField;

public record AddressConfig(
        @ConfigField(value = "host", nullable = true) String host,
        @ConfigField(value = "port", nullable = true) Integer port) {
}
