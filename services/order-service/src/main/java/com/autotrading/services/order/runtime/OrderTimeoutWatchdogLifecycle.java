package com.autotrading.services.order.runtime;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import com.autotrading.services.order.core.OrderSafetyEngine;

/**
 * Spring {@link SmartLifecycle} that periodically invokes
 * {@link OrderSafetyEngine#runTimeoutWatchdog} so that open orders
 * whose first-status has not arrived within the deadline are
 * automatically transitioned to {@code UNKNOWN_PENDING_RECON} and
 * trading mode is set to {@code FROZEN}.
 *
 * <p>The interval is configurable via
 * {@code order.timeout.watchdog.interval.ms} (default: 5 000 ms).
 */
public class OrderTimeoutWatchdogLifecycle implements SmartLifecycle {

    private static final Logger log =
            LoggerFactory.getLogger(OrderTimeoutWatchdogLifecycle.class);

    private final OrderSafetyEngine engine;
    private final Clock clock;
    private final long intervalMs;

    private volatile ScheduledExecutorService executor;
    private volatile boolean running = false;

    public OrderTimeoutWatchdogLifecycle(
            OrderSafetyEngine engine,
            Clock clock,
            long intervalMs) {
        this.engine     = engine;
        this.clock      = clock;
        this.intervalMs = intervalMs;
    }

    // -----------------------------------------------------------------------
    // SmartLifecycle
    // -----------------------------------------------------------------------

    @Override
    public void start() {
        log.info("OrderTimeoutWatchdog starting — interval={}ms", intervalMs);
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "order-timeout-watchdog");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::runWatchdog, 0, intervalMs, TimeUnit.MILLISECONDS);
        running = true;
    }

    @Override
    public void stop() {
        log.info("OrderTimeoutWatchdog stopping");
        running = false;
        ScheduledExecutorService ex = executor;
        if (ex != null) {
            ex.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Runs later than most beans so the application is fully started first. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 10;
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private void runWatchdog() {
        try {
            int count = engine.runTimeoutWatchdog(clock.instant());
            if (count > 0) {
                log.warn("OrderTimeoutWatchdog frozen {} order(s) due to first-status timeout", count);
            }
        } catch (Exception e) {
            log.error("OrderTimeoutWatchdog encountered an unexpected error", e);
        }
    }
}
