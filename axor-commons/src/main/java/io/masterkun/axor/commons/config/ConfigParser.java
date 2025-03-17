package io.masterkun.axor.commons.config;

import com.typesafe.config.Config;

public interface ConfigParser {
    Object parseFrom(Config config, String key, TypeRef type);
}
