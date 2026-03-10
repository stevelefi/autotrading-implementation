package com.autotrading.services.ingress.api;

import java.util.Map;

public record IngressSubmitRequest(
    String client_event_id,
    String event_intent,
    String agent_id,
    String integration_id,
    String source_event_id,
    Map<String, Object> payload
) {
}
