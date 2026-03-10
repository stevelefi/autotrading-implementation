package com.autotrading.services.order.core;

import java.time.Instant;

public record OrderIntentState(
    String orderIntentId,
    String clientEventId,
    Instant createdAtUtc,
    Instant submissionDeadlineUtc,
    boolean firstStatusObserved,
    OrderLifecycleState lifecycleState,
    long stateVersion,
    String brokerSubmitId
) {
  public OrderIntentState withStatusObserved(OrderLifecycleState next, Instant nowUtc) {
    return new OrderIntentState(orderIntentId, clientEventId, createdAtUtc, submissionDeadlineUtc, true, next, stateVersion + 1, brokerSubmitId);
  }

  public OrderIntentState withTimeoutFreeze() {
    return new OrderIntentState(orderIntentId, clientEventId, createdAtUtc, submissionDeadlineUtc, false, OrderLifecycleState.UNKNOWN_PENDING_RECON, stateVersion + 1, brokerSubmitId);
  }
}
