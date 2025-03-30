package io.axor.cluster.config;

import io.axor.commons.config.ConfigField;

import java.time.Duration;

public record FailureDetectConfig(
        @ConfigField(value = "enabled", fallback = "true") boolean enabled,
        @ConfigField(value = "pingInterval", fallback = "5s") Duration pingInterval,
        @ConfigField(value = "failCheckInterval", fallback = "5s") Duration failCheckInterval,
        @ConfigField(value = "pingTimeout", fallback = "5s") Duration memberPingTimeout,
        @ConfigField(value = "downTimeout", fallback = "20s") Duration memberDownTimeout,
        @ConfigField(value = "failTimeout", fallback = "5m") Duration memberFailTimeout,
        @ConfigField(value = "removeTimeout", fallback = "1d") Duration memberRemoveTimeout) {
}
