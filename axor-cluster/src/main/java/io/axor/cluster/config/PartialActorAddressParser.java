package io.axor.cluster.config;

import com.typesafe.config.Config;
import io.axor.commons.config.ConfigParser;
import io.axor.commons.config.TypeRef;

import java.net.URI;

public class PartialActorAddressParser implements ConfigParser {
    @Override
    public Object parseFrom(Config config, String key, TypeRef type) {
        return config.getStringList(key).stream()
                .map(s -> "//" + s)
                .map(URI::create)
                .toList();
    }
}
