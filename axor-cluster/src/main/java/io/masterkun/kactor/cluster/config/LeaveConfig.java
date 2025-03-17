package io.masterkun.kactor.cluster.config;

import io.masterkun.kactor.commons.config.ConfigField;

import java.time.Duration;

public record LeaveConfig(
        @ConfigField("reqInterval") Duration reqInterval,
        @ConfigField("timeout") Duration timeout) {
}
