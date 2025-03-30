package io.axor.config;

import com.typesafe.config.Config;
import io.axor.commons.config.ConfigField;
import io.axor.commons.config.ConfigOrigin;

public record ActorConfig(
        @ConfigField("logger") LoggerConfig logger,
        @ConfigField("network") NetworkConfig network,
        @ConfigField("runtime") Config runtime,
        @ConfigOrigin Config origin
) {
}
