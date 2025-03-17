package io.masterkun.axor.cluster.config;

import io.masterkun.axor.commons.config.ConfigField;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public record JoinConfig(
        @ConfigField("autoJoin") boolean autoJoin,
        @ConfigField("reqInterval") Duration reqInterval,
        @ConfigField(value = "seeds", typeArges = URI.class) List<URI> seeds) {

    public JoinConfig(boolean autoJoin, Duration reqInterval, String... seeds) {
        this(autoJoin, reqInterval, Arrays.stream(seeds).map(URI::create).toList());
    }
}
