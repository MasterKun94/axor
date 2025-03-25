package io.masterkun.axor.cluster.config;

import io.masterkun.axor.commons.config.ConfigField;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public record JoinConfig(
        @ConfigField(value = "autoJoin", fallback = "true") boolean autoJoin,
        @ConfigField(value = "reqInterval", fallback = "5s") Duration reqInterval,
        @ConfigField(value = "seeds", parser = PartialActorAddressParser.class) List<URI> seeds) {

    public JoinConfig(boolean autoJoin, Duration reqInterval, String... seeds) {
        this(autoJoin, reqInterval, Arrays.stream(seeds).map(URI::create).toList());
    }
}
