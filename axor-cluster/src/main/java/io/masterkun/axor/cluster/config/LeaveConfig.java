package io.masterkun.axor.cluster.config;

import io.masterkun.axor.commons.config.ConfigField;

import java.time.Duration;

public record LeaveConfig(
        @ConfigField("reqInterval") Duration reqInterval,
        @ConfigField("timeout") Duration timeout) {
}
