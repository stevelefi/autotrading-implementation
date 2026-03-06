package com.autotrading.services.agentruntime.core;

import com.autotrading.command.v1.EvaluateSignalRequest;
import com.autotrading.command.v1.RequestContext;
import java.util.Map;

public class RoutedToSignalAdapter {

  public EvaluateSignalRequest toEvaluateSignalRequest(RoutedSignalInput input) {
    Object side = input.payload().getOrDefault("side", "BUY");
    Object qty = input.payload().getOrDefault("qty", 1);

    return EvaluateSignalRequest.newBuilder()
        .setRequestContext(RequestContext.newBuilder()
            .setTraceId(input.traceId())
            .setRequestId(input.requestId())
            .setIdempotencyKey(input.idempotencyKey())
            .setPrincipalId(input.principalId())
            .build())
        .setAgentId(input.agentId())
        .setInstrumentId(String.valueOf(input.payload().getOrDefault("instrument_id", "eq_tqqq")))
        .setSignalId(input.signalId())
        .setSide(String.valueOf(side))
        .setQty(Long.parseLong(String.valueOf(qty)))
        .setStrategyTs(input.strategyTs())
        .setOrderType(String.valueOf(input.payload().getOrDefault("order_type", "MKT")))
        .setTimeInForce(String.valueOf(input.payload().getOrDefault("time_in_force", "DAY")))
        .setReason(String.valueOf(input.payload().getOrDefault("reason", "strategy-signal")))
        .setTradeEventId(input.tradeEventId())
        .setRawEventId(input.rawEventId())
        .setOriginSourceType(input.originSourceType())
        .setOriginSourceEventId(input.originSourceEventId())
        .setSourceSystem(input.sourceSystem())
        .build();
  }

  public record RoutedSignalInput(
      String traceId,
      String requestId,
      String idempotencyKey,
      String principalId,
      String agentId,
      String signalId,
      String strategyTs,
      String tradeEventId,
      String rawEventId,
      String originSourceType,
      String originSourceEventId,
      String sourceSystem,
      Map<String, Object> payload
  ) {
  }
}
