package io.masterkun.kactor.config;

import io.masterkun.kactor.commons.config.ConfigField;

public record LoggerConfig(
        @ConfigField("mdcEnabled") boolean mdcEnabled,
        @ConfigField("logDeadLetters") boolean logDeadLetters,
        @ConfigField("logSystemEvents") boolean logSystemEvents) {
}
