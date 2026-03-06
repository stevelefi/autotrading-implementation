package com.autotrading.services.ibkr;

import static org.assertj.core.api.Assertions.assertThat;

import com.autotrading.command.v1.RequestContext;
import com.autotrading.command.v1.SubmitOrderRequest;
import com.autotrading.command.v1.CommandStatus;
import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import org.junit.jupiter.api.Test;

class BrokerConnectorEngineTest {

  @Test
  void grpcRetryWithSameKeyDoesNotDuplicateSubmit() {
    BrokerConnectorEngine engine = new BrokerConnectorEngine();

    SubmitOrderRequest request = SubmitOrderRequest.newBuilder()
        .setRequestContext(RequestContext.newBuilder()
            .setTraceId("trc-1")
            .setRequestId("req-1")
            .setIdempotencyKey("idem-1")
            .setPrincipalId("svc-order")
            .build())
        .setAgentId("agent-1")
        .setInstrumentId("eq_tqqq")
        .setOrderIntentId("ord-1")
        .setSide("BUY")
        .setQty(10)
        .setOrderType("MKT")
        .setTimeInForce("DAY")
        .setSubmissionDeadlineMs(60000)
        .build();

    var first = engine.submit(request);
    var second = engine.submit(request);

    assertThat(first.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
    assertThat(second.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_DUPLICATE);
    assertThat(engine.submitCount("ord-1")).isEqualTo(1);
  }

  @Test
  void duplicateExecCallbackIsIgnored() {
    BrokerConnectorEngine engine = new BrokerConnectorEngine();

    boolean first = engine.recordExecution("exec-1");
    boolean second = engine.recordExecution("exec-1");

    assertThat(first).isTrue();
    assertThat(second).isFalse();
  }
}
