package com.autotrading.services.ingress.api;

public record IngressAcceptedResponse(String event_id, Data data) {
  public record Data(
      boolean accepted,
      String received_at,
      String status
  ) {
  }
}
