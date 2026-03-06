package com.autotrading.services.order.core;

/**
 * All possible states in the order lifecycle.
 */
public enum OrderLifecycleState {
  INTENT_CREATED,
  SUBMIT_REQUESTED,
  SUBMITTED_ACKED,
  PARTIALLY_FILLED,
  FILLED,
  CANCELLED,
  EXPIRED,
  UNKNOWN_PENDING_RECON
}
