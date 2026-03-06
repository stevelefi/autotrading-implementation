package com.autotrading.libs.reliability.outbox;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Spring {@link SmartLifecycle} bean that drives the transactional outbox relay loop.
 * <p>
 * When started, it polls {@link OutboxDispatcher#dispatchBatch} every
 * {@value #POLL_INTERVAL_MS} milliseconds on a dedicated daemon thread.
 * The loop is stopped gracefully on {@link #stop()}.
 */
public class OutboxPollerLifecycle implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(OutboxPollerLifecycle.class);
  private static final long POLL_INTERVAL_MS = 500;
  private static final int BATCH_SIZE = 100;

  private final OutboxDispatcher outboxDispatcher;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private ScheduledExecutorService scheduler;

  public OutboxPollerLifecycle(OutboxDispatcher outboxDispatcher) {
    this.outboxDispatcher = outboxDispatcher;
  }

  @Override
  public void start() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    log.info("outbox-poller starting (interval={}ms, batchSize={})", POLL_INTERVAL_MS, BATCH_SIZE);
    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "outbox-poller");
      t.setDaemon(true);
      return t;
    });
    scheduler.scheduleWithFixedDelay(this::pollOnce, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
  }

  @Override
  public void stop() {
    if (!running.compareAndSet(true, false)) {
      return;
    }
    log.info("outbox-poller stopping");
    if (scheduler != null) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  /** Phase > 0 so outbox poller starts AFTER all Kafka listeners are bound. */
  @Override
  public int getPhase() {
    return Integer.MAX_VALUE - 1000;
  }

  private void pollOnce() {
    try {
      int dispatched = outboxDispatcher.dispatchBatch(BATCH_SIZE);
      if (dispatched > 0) {
        log.debug("outbox-poller dispatched {} events", dispatched);
      }
    } catch (Exception e) {
      log.warn("outbox-poller error: {}", e.getMessage(), e);
    }
  }
}
