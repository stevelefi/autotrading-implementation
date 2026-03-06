package com.autotrading.services.ingress.api;

import com.autotrading.services.ingress.core.IngressService;
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
    IngressAcceptedResponse response = ingressService.accept(request, requestId, authorization);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }
}
