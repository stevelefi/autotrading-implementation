package com.autotrading.libs.reliability.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ReliabilityMetricsTest {

    // -------------------------------------------------------------------
    // No-arg constructor (null registry — skips meter registration)
    // -------------------------------------------------------------------

    @Test
    void incrementDuplicateSuppressionCountReturnsCumulativeValue() {
        ReliabilityMetrics metrics = new ReliabilityMetrics();
        assertThat(metrics.incrementDuplicateSuppressionCount()).isEqualTo(1L);
        assertThat(metrics.incrementDuplicateSuppressionCount()).isEqualTo(2L);
        assertThat(metrics.duplicateSuppressionCount()).isEqualTo(2L);
    }

    @Test
    void incrementFirstStatusTimeoutCountReturnsCumulativeValue() {
        ReliabilityMetrics metrics = new ReliabilityMetrics();
        assertThat(metrics.incrementFirstStatusTimeoutCount()).isEqualTo(1L);
        assertThat(metrics.incrementFirstStatusTimeoutCount()).isEqualTo(2L);
        assertThat(metrics.firstStatusTimeoutCount()).isEqualTo(2L);
    }

    @Test
    void setOutboxBacklogAgeMsStoresValue() {
        ReliabilityMetrics metrics = new ReliabilityMetrics();
        metrics.setOutboxBacklogAgeMs(5000L);
        assertThat(metrics.outboxBacklogAgeMs()).isEqualTo(5000L);
    }

    @Test
    void setOutboxBacklogAgeMsClipsNegativeToZero() {
        ReliabilityMetrics metrics = new ReliabilityMetrics();
        metrics.setOutboxBacklogAgeMs(-1L);
        assertThat(metrics.outboxBacklogAgeMs()).isEqualTo(0L);
    }

    @Test
    void resetClearsAllCounters() {
        ReliabilityMetrics metrics = new ReliabilityMetrics();
        metrics.incrementDuplicateSuppressionCount();
        metrics.incrementFirstStatusTimeoutCount();
        metrics.setOutboxBacklogAgeMs(9999L);

        metrics.reset();

        assertThat(metrics.duplicateSuppressionCount()).isEqualTo(0L);
        assertThat(metrics.firstStatusTimeoutCount()).isEqualTo(0L);
        assertThat(metrics.outboxBacklogAgeMs()).isEqualTo(0L);
    }

    // -------------------------------------------------------------------
    // With a real registry — exercises the registerMeters() path
    // -------------------------------------------------------------------

    @Test
    void registersThreeGaugesWhenMeterRegistryProvided() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReliabilityMetrics metrics = new ReliabilityMetrics(registry);

        metrics.incrementDuplicateSuppressionCount();
        metrics.incrementFirstStatusTimeoutCount();
        metrics.setOutboxBacklogAgeMs(1234L);

        assertThat(registry.get("autotrading.reliability.duplicate.suppression.count")
                .gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("autotrading.reliability.first.status.timeout.count")
                .gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("autotrading.reliability.outbox.backlog.age.ms")
                .gauge().value()).isEqualTo(1234.0);
    }

    @Test
    void resetAlsoZeroesGaugesInRegistry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReliabilityMetrics metrics = new ReliabilityMetrics(registry);

        metrics.incrementDuplicateSuppressionCount();
        metrics.reset();

        assertThat(registry.get("autotrading.reliability.duplicate.suppression.count")
                .gauge().value()).isEqualTo(0.0);
    }
}
