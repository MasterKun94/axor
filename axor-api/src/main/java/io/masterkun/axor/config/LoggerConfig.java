package io.masterkun.axor.config;

import io.masterkun.axor.commons.config.ConfigField;

public record LoggerConfig(
        @ConfigField("mdcEnabled") boolean mdcEnabled,
        @ConfigField("logDeadLetters") boolean logDeadLetters,
        @ConfigField("logSystemEvents") boolean logSystemEvents) {
}
