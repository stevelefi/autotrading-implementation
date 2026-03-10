package com.autotrading.services.eventprocessor.core;

import java.time.Instant;
import java.util.Map;

public record RoutedTradeEvent(
    String tradeEventId,
    String eventId,
    String rawEventId,
    String traceId,
    String clientEventId,
    String sourceType,
    String sourceEventId,
    String agentId,
    Instant routedAt,
    Map<String, Object> payload
) {
}
