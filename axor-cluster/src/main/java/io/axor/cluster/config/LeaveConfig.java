package io.axor.cluster.config;

import io.axor.commons.config.ConfigField;

import java.time.Duration;

public record LeaveConfig(
        @ConfigField(value = "reqInterval", fallback = "2s") Duration reqInterval,
        @ConfigField(value = "timeout", fallback = "10s") Duration timeout) {
}
