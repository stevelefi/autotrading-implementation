package com.autotrading.services.ibkr;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autotrading.command.v1.CancelOrderRequest;
import com.autotrading.command.v1.CommandStatus;
import com.autotrading.command.v1.ReplaceOrderRequest;
import com.autotrading.command.v1.RequestContext;
import com.autotrading.command.v1.SubmitOrderRequest;
import com.autotrading.libs.idempotency.InMemoryIdempotencyService;
import com.autotrading.libs.kafka.DirectKafkaPublisher;
import com.autotrading.services.ibkr.client.IbkrHealthProbe;
import com.autotrading.services.ibkr.client.IbkrRestClient;
import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import com.autotrading.services.ibkr.db.BrokerOrderRepository;
import com.autotrading.services.ibkr.db.ExecutionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class BrokerConnectorEngineTest {

  private BrokerConnectorEngine engine;
  private IbkrHealthProbe mockHealthProbe;

  @BeforeEach
  void setUp() {
    BrokerOrderRepository mockRepo = mock(BrokerOrderRepository.class);
    lenient().when(mockRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    DirectKafkaPublisher mockPublisher = mock(DirectKafkaPublisher.class);
    mockHealthProbe = mock(IbkrHealthProbe.class);
    lenient().when(mockHealthProbe.isUp()).thenReturn(true);
    engine = new BrokerConnectorEngine(
        new InMemoryIdempotencyService(),
        mockRepo,
        mock(ExecutionRepository.class),
        mockPublisher,
        new ObjectMapper(),
        mockHealthProbe,
        mock(IbkrRestClient.class),
        true /* simulatorMode */);
  }

  @Test
  void grpcRetryWithSameKeyDoesNotDuplicateSubmit() {
    SubmitOrderRequest request = SubmitOrderRequest.newBuilder()
        .setRequestContext(RequestContext.newBuilder()
            .setTraceId("trc-1")
            .setRequestId("req-1")
            .setClientEventId("idem-1")
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
  void sameClientEventIdWithDifferentPayloadReturnsDuplicate() {
    SubmitOrderRequest first = baseSubmitRequest("idem-conflict", "ord-1", 10);
    SubmitOrderRequest conflicting = baseSubmitRequest("idem-conflict", "ord-1", 20);

    var accepted = engine.submit(first);
    var duplicate = engine.submit(conflicting);

    assertThat(accepted.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
    assertThat(duplicate.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_DUPLICATE);
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
        .setClientEventId(idem)
        .setPrincipalId("svc-order")
        .build();
  }

  /** Builds an engine in REST mode (simulatorMode=false) with a mockable health probe. */
  private BrokerConnectorEngine buildRestModeEngine(IbkrHealthProbe probe, IbkrRestClient client) {
    BrokerOrderRepository mockRepo = mock(BrokerOrderRepository.class);
    lenient().when(mockRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    return new BrokerConnectorEngine(
        new InMemoryIdempotencyService(),
        mockRepo,
        mock(ExecutionRepository.class),
        mock(DirectKafkaPublisher.class),
        new ObjectMapper(),
        probe,
        client,
        false /* simulatorMode */);
  }

  @Test
  void restMode_brokerUnavailable_returnsFailedStatus() {
    IbkrHealthProbe downProbe = mock(IbkrHealthProbe.class);
    when(downProbe.isUp()).thenReturn(false);

    BrokerConnectorEngine restEngine = buildRestModeEngine(downProbe, mock(IbkrRestClient.class));

    var response = restEngine.submit(baseSubmitRequest("idem-down", "ord-down", 5));

    assertThat(response.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_FAILED);
    assertThat(response.getBrokerSubmitId()).isEmpty();
  }

  @Test
  void simulatorMode_probeDown_stillAccepts() {
    // In simulator mode the health probe check is bypassed
    when(mockHealthProbe.isUp()).thenReturn(false);

    var response = engine.submit(baseSubmitRequest("idem-sim-down", "ord-sim-down", 5));

    assertThat(response.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
  }
}
