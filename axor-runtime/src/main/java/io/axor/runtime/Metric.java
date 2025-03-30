package io.axor.runtime;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.observation.ObservationRegistry;

public class Metric {

    private static final ObservationRegistry registry = ObservationRegistry.create();

    public static ObservationRegistry observationRegistry() {
        return registry;
    }

    public static MeterRegistry registry() {
        return Metrics.globalRegistry;
    }
}
