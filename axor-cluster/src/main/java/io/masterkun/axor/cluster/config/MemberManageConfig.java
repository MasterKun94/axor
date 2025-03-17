package io.masterkun.axor.cluster.config;

import io.masterkun.axor.commons.config.ConfigField;

public record MemberManageConfig(
        @ConfigField("publishRate") double publishRate,
        @ConfigField("publishNumMin") int publishNumMin) {
}
