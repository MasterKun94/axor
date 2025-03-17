package io.masterkun.axor.config;

import com.typesafe.config.Config;
import io.masterkun.axor.commons.config.ConfigField;
import io.masterkun.axor.commons.config.ConfigOrigin;

public record ActorConfig(
        @ConfigField("logger") LoggerConfig logger,
        @ConfigField("network") NetworkConfig network,
        @ConfigField("runtime") Config runtime,
        @ConfigOrigin Config origin
) {
}
