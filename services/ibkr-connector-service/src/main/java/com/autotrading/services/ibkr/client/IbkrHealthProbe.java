package com.autotrading.services.ibkr.client;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Scheduled health probe that calls {@code GET /v1/api/tickle} to keep the IBKR Client Portal
 * session alive and track broker connectivity.
 *
 * <p>Phase {@code 100} — starts before {@link IbkrOrderPoller} (phase 150) and the gRPC server
 * lifecycle (phase 200), so {@link #isUp()} is reliable before the service begins accepting calls.
 *
 * <p>In simulator mode: treated as UP until first probe completes (avoids startup race).
 */
public class IbkrHealthProbe implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(IbkrHealthProbe.class);
  private static final int PHASE = 100;

  private final IbkrRestClient restClient;
  private final long tickleIntervalMs;
  private final boolean simulatorMode;

  private volatile BrokerStatus status = BrokerStatus.UNKNOWN;
  private ScheduledExecutorService scheduler;

  public IbkrHealthProbe(IbkrRestClient restClient, long tickleIntervalMs, boolean simulatorMode) {
    this.restClient = restClient;
    this.tickleIntervalMs = tickleIntervalMs;
    this.simulatorMode = simulatorMode;
  }

  // ------------------------------------------------------------------
  // SmartLifecycle
  // ------------------------------------------------------------------

  @Override
  public void start() {
    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "ibkr-health-probe");
      t.setDaemon(true);
      return t;
    });
    scheduler.scheduleAtFixedRate(this::runTickle, 0, tickleIntervalMs, TimeUnit.MILLISECONDS);
    log.info("IbkrHealthProbe started tickleIntervalMs={} simulatorMode={}", tickleIntervalMs, simulatorMode);
  }

  @Override
  public void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
    status = BrokerStatus.UNKNOWN;
    log.info("IbkrHealthProbe stopped");
  }

  @Override
  public boolean isRunning() {
    return scheduler != null && !scheduler.isShutdown();
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public int getPhase() {
    return PHASE;
  }

  // ------------------------------------------------------------------
  // Public API
  // ------------------------------------------------------------------

  /**
   * Returns {@code true} if the broker is available.
   *
   * <p>In simulator mode, {@link BrokerStatus#UNKNOWN} (probe hasn't completed yet) is also
   * treated as UP to avoid blocking startup ordering races.
   */
  public boolean isUp() {
    if (simulatorMode) {
      return status != BrokerStatus.DOWN;
    }
    return status == BrokerStatus.UP;
  }

  public BrokerStatus getStatus() {
    return status;
  }

  // ------------------------------------------------------------------
  // Internal (package-private for tests, public for cross-package test access)
  // ------------------------------------------------------------------

  public void runTickle() {
    try {
      var response = restClient.tickle();
      BrokerStatus previous = status;
      if (response != null && response.isAuthenticated()) {
        status = BrokerStatus.UP;
        if (previous != BrokerStatus.UP) {
          log.info("IbkrHealthProbe broker UP (transition from {})", previous);
        }
      } else {
        status = BrokerStatus.DOWN;
        if (previous != BrokerStatus.DOWN) {
          log.warn("IbkrHealthProbe broker DOWN — tickle returned unauthenticated (previous={})", previous);
        }
      }
    } catch (Exception e) {
      BrokerStatus previous = status;
      status = BrokerStatus.DOWN;
      if (previous != BrokerStatus.DOWN) {
        log.warn("IbkrHealthProbe broker DOWN — tickle failed (previous={}): {}", previous, e.getMessage());
      }
    }
  }
}
