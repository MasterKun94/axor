package io.masterkun.kactor.config;

import com.typesafe.config.Config;
import io.masterkun.kactor.commons.config.ConfigField;
import io.masterkun.kactor.commons.config.ConfigOrigin;

public record ActorConfig(
        @ConfigField("logger") LoggerConfig logger,
        @ConfigField("network") NetworkConfig network,
        @ConfigField("runtime") Config runtime,
        @ConfigOrigin Config origin
) {
}
