package com.invoiceapp.backend.shared.kafka;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@Profile("!test")
public class ConsumerLagMetrics {

    private final AtomicLong maxLag = new AtomicLong(0);

    public ConsumerLagMetrics(MeterRegistry registry) {
        Gauge.builder("kafka.consumer.max.lag", maxLag, AtomicLong::doubleValue)
                .description("Highest lag across this consumer's assigned partitions")
                .tag("consumer", "sse-listener")
                .register(registry);
    }

    public void update(long lag) {
        maxLag.set(lag);
    }
}