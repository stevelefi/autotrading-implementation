package com.autotrading.services.ibkr.api;

import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/smoke")
public class IbkrSmokeController {
  private final BrokerConnectorEngine engine;

  public IbkrSmokeController(BrokerConnectorEngine engine) {
    this.engine = engine;
  }

  @GetMapping("/stats")
  public Map<String, Object> stats() {
    return Map.of(
        "timestamp_utc", Instant.now().toString(),
        "total_submit_count", engine.totalSubmitCount());
  }
}
