package com.invoiceapp.backend.shared.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsumerLagMetrics")
class ConsumerLagMetricsTest {

    private MeterRegistry registry;
    private ConsumerLagMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new ConsumerLagMetrics(registry);
    }

    @Test
    @DisplayName("registers the gauge at zero")
    void starts_at_zero() {
        double value = registry.get("kafka.consumer.max.lag")
                .tag("consumer", "sse-listener")
                .gauge().value();

        assertThat(value).isZero();
    }

    @Test
    @DisplayName("reflects the latest lag value")
    void updates() {
        metrics.update(42L);

        double value = registry.get("kafka.consumer.max.lag")
                .tag("consumer", "sse-listener")
                .gauge().value();

        assertThat(value).isEqualTo(42.0);
    }
}