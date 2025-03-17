package io.masterkun.kactor.commons.config;

import com.typesafe.config.Config;

public interface ConfigParser {
    Object parseFrom(Config config, String key, TypeRef type);
}
