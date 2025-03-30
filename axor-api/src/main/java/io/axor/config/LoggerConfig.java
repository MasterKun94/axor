package io.axor.config;

import io.axor.commons.config.ConfigField;

public record LoggerConfig(
        @ConfigField("mdcEnabled") boolean mdcEnabled,
        @ConfigField("logDeadLetters") boolean logDeadLetters,
        @ConfigField("logSystemEvents") boolean logSystemEvents) {
}
