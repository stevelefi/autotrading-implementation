package com.autotrading.services.ingress.api;

public record IngressAcceptedResponse(String trace_id, Data data) {
  public record Data(
      boolean accepted,
      String ingress_event_id,
      String received_at,
      String status
  ) {
  }
}
