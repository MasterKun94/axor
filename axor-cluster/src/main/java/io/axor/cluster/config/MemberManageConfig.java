package io.axor.cluster.config;

import io.axor.commons.config.ConfigField;

public record MemberManageConfig(
        @ConfigField(value = "publishRate", fallback = "0.8") double publishRate,
        @ConfigField(value = "publishNumMin", fallback = "10") int publishNumMin) {
}
