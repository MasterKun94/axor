package io.axor.persistence.jdbc;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class Utils {
    public static Config resolveConfig(Config config) {
        return config.withFallback(ConfigFactory.parseResources("axor-persistence-jdbc.conf"))
                .withFallback(ConfigFactory.parseResources("axor-persistence.conf"))
                .resolve();
    }
}
