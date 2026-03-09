package com.autotrading.services.ingress.api;

import com.autotrading.services.ingress.core.IngressService;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ingress/v1")
public class IngressController {
  private final IngressService ingressService;

  public IngressController(IngressService ingressService) {
    this.ingressService = ingressService;
  }

  @PostMapping("/events")
  public ResponseEntity<IngressAcceptedResponse> publishEvent(
      @RequestHeader("Authorization") String authorization,
      @RequestHeader("X-Request-Id") String requestId,
      @RequestBody IngressSubmitRequest request) {
    if (request.agent_id() != null) MDC.put("agent_id", request.agent_id());
    IngressAcceptedResponse response = ingressService.accept(request, requestId, authorization);
    // Populate MDC with the unified trace_id (snake_case) used by all downstream
    // consumers and scripts/trace.py — matches the OTel trace ID stored in the DB
    // and propagated through every Kafka envelope.
    MDC.put("trace_id", response.trace_id());
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }
}
