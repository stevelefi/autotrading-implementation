package com.autotrading.services.order.core;

public enum TradingMode {
  /** Normal operation — orders flow through. */
  NORMAL,
  /**
   * Circuit open — broker returned UNAVAILABLE on the last submit attempt.
   * New orders are rejected. Auto-clears to {@link #NORMAL} on the next successful broker submit.
   */
  CIRCUIT_OPEN,
  /**
   * Hard freeze — a 60 s first-status timeout was observed.
   * Requires manual reset via {@code POST /internal/smoke/reset}.
   */
  FROZEN
}
