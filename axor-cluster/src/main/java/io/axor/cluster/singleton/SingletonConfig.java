package io.axor.cluster.singleton;

import io.axor.commons.config.ConfigField;

import java.time.Duration;
import java.util.List;

public record SingletonConfig(
        @ConfigField(typeArges = String.class, fallback = "[]") List<String> requireRoles,
        @ConfigField(fallback = "1s") Duration instanceReadyReqDelay,
        @ConfigField(fallback = "3s") Duration instanceReadyReqInterval,
        @ConfigField(fallback = "10s") Duration stopTimeout) {
}
