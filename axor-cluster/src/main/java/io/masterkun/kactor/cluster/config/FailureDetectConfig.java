package io.masterkun.kactor.cluster.config;

import io.masterkun.kactor.commons.config.ConfigField;

import java.time.Duration;

public record FailureDetectConfig(
        @ConfigField("enabled") boolean enabled,
        @ConfigField("pingInterval") Duration pingInterval,
        @ConfigField("failCheckInterval") Duration failCheckInterval,
        @ConfigField("pingTimeout") Duration memberPingTimeout,
        @ConfigField("downTimeout") Duration memberDownTimeout,
        @ConfigField("failTimeout") Duration memberFailTimeout,
        @ConfigField("removeTimeout") Duration memberRemoveTimeout) {
}
