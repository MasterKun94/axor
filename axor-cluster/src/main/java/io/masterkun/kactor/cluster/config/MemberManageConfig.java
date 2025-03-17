package io.masterkun.kactor.cluster.config;

import io.masterkun.kactor.commons.config.ConfigField;

public record MemberManageConfig(
        @ConfigField("publishRate") double publishRate,
        @ConfigField("publishNumMin") int publishNumMin) {
}
