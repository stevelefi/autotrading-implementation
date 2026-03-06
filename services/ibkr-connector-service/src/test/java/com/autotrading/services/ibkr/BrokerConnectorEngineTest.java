package com.autotrading.services.ibkr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.autotrading.command.v1.RequestContext;
import com.autotrading.command.v1.CancelOrderRequest;
import com.autotrading.command.v1.ReplaceOrderRequest;
import com.autotrading.command.v1.SubmitOrderRequest;
import com.autotrading.command.v1.CommandStatus;
import com.autotrading.libs.idempotency.InMemoryIdempotencyService;
import com.autotrading.libs.reliability.outbox.OutboxRepository;
import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import com.autotrading.services.ibkr.db.BrokerOrderEntity;
import com.autotrading.services.ibkr.db.BrokerOrderRepository;
import com.autotrading.services.ibkr.db.ExecutionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BrokerConnectorEngineTest {

  private BrokerConnectorEngine engine;

  @BeforeEach
  void setUp() {
    BrokerOrderRepository mockRepo = mock(BrokerOrderRepository.class);
    lenient().when(mockRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    OutboxRepository mockOutbox = mock(OutboxRepository.class);
    engine = new BrokerConnectorEngine(
        new InMemoryIdempotencyService(),
        mockRepo,
        mock(ExecutionRepository.class),
        mockOutbox,
        new ObjectMapper());
  }

  @Test
  void grpcRetryWithSameKeyDoesNotDuplicateSubmit() {
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
    boolean first = engine.recordExecution("exec-1");
    boolean second = engine.recordExecution("exec-1");

    assertThat(first).isTrue();
    assertThat(second).isFalse();
  }

  @Test
  void sameIdempotencyKeyWithDifferentPayloadIsRejected() {
    SubmitOrderRequest first = baseSubmitRequest("idem-conflict", "ord-1", 10);
    SubmitOrderRequest conflicting = baseSubmitRequest("idem-conflict", "ord-1", 20);

    var accepted = engine.submit(first);
    var rejected = engine.submit(conflicting);

    assertThat(accepted.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
    assertThat(rejected.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_REJECTED);
    assertThat(engine.submitCount("ord-1")).isEqualTo(1);
  }

  @Test
  void cancelAndReplaceAreAccepted() {
    var cancel = engine.cancel(CancelOrderRequest.newBuilder()
        .setRequestContext(baseContext("idem-cancel"))
        .setAgentId("agent-1")
        .setOrderIntentId("ord-1")
        .setReason("test")
        .build());
    var replace = engine.replace(ReplaceOrderRequest.newBuilder()
        .setRequestContext(baseContext("idem-replace"))
        .setAgentId("agent-1")
        .setOrderIntentId("ord-1")
        .setNewQty(15)
        .setNewLimitPrice("41.23")
        .setReason("test")
        .build());

    assertThat(cancel.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
    assertThat(cancel.getBrokerCancelId()).startsWith("broker-cancel-");
    assertThat(replace.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
    assertThat(replace.getBrokerReplaceId()).startsWith("broker-replace-");
  }

  private static SubmitOrderRequest baseSubmitRequest(String idem, String orderIntentId, int qty) {
    return SubmitOrderRequest.newBuilder()
        .setRequestContext(baseContext(idem))
        .setAgentId("agent-1")
        .setInstrumentId("eq_tqqq")
        .setOrderIntentId(orderIntentId)
        .setSide("BUY")
        .setQty(qty)
        .setOrderType("MKT")
        .setTimeInForce("DAY")
        .setSubmissionDeadlineMs(60000)
        .build();
  }

  private static RequestContext baseContext(String idem) {
    return RequestContext.newBuilder()
        .setTraceId("trc-1")
        .setRequestId("req-1")
        .setIdempotencyKey(idem)
        .setPrincipalId("svc-order")
        .build();
  }
}
