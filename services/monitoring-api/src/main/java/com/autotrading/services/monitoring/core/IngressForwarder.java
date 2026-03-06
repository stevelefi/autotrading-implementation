package com.autotrading.services.monitoring.core;

import java.util.Map;

public interface IngressForwarder {
  Map<String, Object> forward(String idempotencyKey, Map<String, Object> payload, String sourceType);
}
