package io.axor.runtime.timer;

import io.axor.commons.config.ConfigField;

import java.time.Duration;

public record HashedWheelTimerConfig(
        @ConfigField(value = "tickDuration", fallback = "100ms") Duration tickDuration,
        @ConfigField(value = "ticksPerWheel", fallback = "512") int ticksPerWheel,
        @ConfigField(value = "maxPendingTimeouts", fallback = "-1") long maxPendingTimeouts,
        @ConfigField(value = "leakDetection", fallback = "true") boolean leakDetection) {
}
