package com.autotrading.services.order.core;

public enum OrderLifecycleState {
  INTENT_CREATED,
  SUBMIT_REQUESTED,
  SUBMITTED_ACKED,
  UNKNOWN_PENDING_RECON
}
