package com.autotrading.services.ibkr.client;

/**
 * Health status of the IBKR Client Portal REST API connection.
 *
 * <ul>
 *   <li>{@link #UNKNOWN} — probe has not completed its first successful check yet.</li>
 *   <li>{@link #UP} — {@code /tickle} returned {@code authenticated=true, connected=true}.</li>
 *   <li>{@link #DOWN} — last tickle attempt failed or returned unauthenticated.</li>
 * </ul>
 */
public enum BrokerStatus {
  UNKNOWN,
  UP,
  DOWN
}
