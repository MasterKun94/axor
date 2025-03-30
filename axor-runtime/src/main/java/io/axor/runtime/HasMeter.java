package io.axor.runtime;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * The {@code HasMeter} interface is designed for components that can register themselves with a
 * {@link MeterRegistry} to enable metric collection. Implementing this interface allows the
 * component to expose its metrics to monitoring and observability systems, such as Micrometer.
 *
 * <p>Implementations of this interface should provide a method to register their metrics with a
 * given {@code MeterRegistry}. This is useful for tracking performance, health, and other
 * operational characteristics of the component.
 *
 * @see MeterRegistry
 */
public interface HasMeter {
    void register(MeterRegistry registry, String... tags);
}
