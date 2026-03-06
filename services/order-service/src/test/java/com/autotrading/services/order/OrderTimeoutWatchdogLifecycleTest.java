package com.autotrading.services.order;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.autotrading.libs.idempotency.InMemoryIdempotencyService;
import com.autotrading.services.order.db.OrderIntentRepository;
import com.autotrading.services.order.db.OrderLedgerRepository;
import com.autotrading.services.order.db.OrderStateHistoryRepository;
import org.junit.jupiter.api.Test;

import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.services.order.core.OrderSafetyEngine;
import com.autotrading.services.order.runtime.OrderTimeoutWatchdogLifecycle;

class OrderTimeoutWatchdogLifecycleTest {

    private final ReliabilityMetrics metrics = new ReliabilityMetrics();
    private final Clock clock = Clock.systemUTC();
    private final OrderSafetyEngine engine = new OrderSafetyEngine(metrics, clock,
            new InMemoryIdempotencyService(),
            mock(OrderIntentRepository.class),
            mock(OrderLedgerRepository.class),
            mock(OrderStateHistoryRepository.class));

    @Test
    void isNotRunningBeforeStart() {
        OrderTimeoutWatchdogLifecycle watchdog =
                new OrderTimeoutWatchdogLifecycle(engine, clock, 5000L);
        assertThat(watchdog.isRunning()).isFalse();
    }

    @Test
    void startSetsRunningAndStopClearsIt() throws InterruptedException {
        OrderTimeoutWatchdogLifecycle watchdog =
                new OrderTimeoutWatchdogLifecycle(engine, clock, 50L);

        watchdog.start();
        assertThat(watchdog.isRunning()).isTrue();

        // Let the scheduler fire at least once
        Thread.sleep(200);

        watchdog.stop();
        assertThat(watchdog.isRunning()).isFalse();
    }

    @Test
    void phaseIsCloseToIntegerMaxValue() {
        OrderTimeoutWatchdogLifecycle watchdog =
                new OrderTimeoutWatchdogLifecycle(engine, clock, 5000L);
        assertThat(watchdog.getPhase()).isGreaterThan(Integer.MAX_VALUE - 1000);
    }

    @Test
    void watchdogRunsWithoutExceptionWhenNoOrdersPending() throws InterruptedException {
        // 10 ms interval — runs many times during 300 ms sleep — no orders → no timeouts
        OrderTimeoutWatchdogLifecycle watchdog =
                new OrderTimeoutWatchdogLifecycle(engine, clock, 10L);
        watchdog.start();
        Thread.sleep(300);
        watchdog.stop();
        // If no exception, the watchdog handled empty-order list correctly
        assertThat(metrics.firstStatusTimeoutCount()).isZero();
    }
}
