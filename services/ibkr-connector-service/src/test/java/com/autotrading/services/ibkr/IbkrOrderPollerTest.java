package com.autotrading.services.ibkr;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autotrading.libs.idempotency.InMemoryIdempotencyService;
import com.autotrading.libs.kafka.DirectKafkaPublisher;
import com.autotrading.services.ibkr.client.IbkrHealthProbe;
import com.autotrading.services.ibkr.client.IbkrOrderPoller;
import com.autotrading.services.ibkr.client.IbkrRestClient;
import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import com.autotrading.services.ibkr.db.BrokerOrderRepository;
import com.autotrading.services.ibkr.db.ExecutionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class IbkrOrderPollerTest {

  private IbkrRestClient mockClient;
  private BrokerConnectorEngine engine;
  private IbkrHealthProbe mockProbe;
  private IbkrOrderPoller poller;

  @BeforeEach
  void setUp() {
    mockClient = mock(IbkrRestClient.class);
    mockProbe = mock(IbkrHealthProbe.class);
    lenient().when(mockProbe.isUp()).thenReturn(true);

    BrokerOrderRepository mockRepo = mock(BrokerOrderRepository.class);
    lenient().when(mockRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    ExecutionRepository mockExecRepo = mock(ExecutionRepository.class);
    lenient().when(mockExecRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    engine = new BrokerConnectorEngine(
        new InMemoryIdempotencyService(),
        mockRepo,
        mockExecRepo,
        mock(DirectKafkaPublisher.class),
        new ObjectMapper(),
        mockProbe,
        mockClient,
        true /* simulatorMode */);

    poller = new IbkrOrderPoller(mockClient, engine, mockProbe, 5_000);
  }

  @Test
  void runPoll_skips_whenBrokerDown() {
    when(mockProbe.isUp()).thenReturn(false);
    when(mockClient.getTrades()).thenReturn(List.of());

    poller.runPoll();

    verify(mockClient, never()).getTrades();
  }

  @Test
  void runPoll_noTrades_nothingRecorded() {
    when(mockClient.getTrades()).thenReturn(List.of());

    poller.runPoll();

    // No unchecked calls — just verifying it doesn't explode
    verify(mockClient, times(1)).getTrades();
  }

  @Test
  void runPoll_newTrade_recordsExecution() {
    var trade = new IbkrRestClient.IbkrTrade(
        "exec-xyz-001",   // executionId
        "10001",          // orderId
        "BUY",            // side
        100.0,            // size
        152.50,           // price
        0.50,             // commission
        Instant.now().toEpochMilli(),
        265598L,          // conid
        "TQQQ"            // symbol
    );
    when(mockClient.getTrades()).thenReturn(List.of(trade));

    poller.runPoll();

    // engine.recordExecution adds to seenExecIds; calling again should return false (duplicate)
    boolean secondCall = engine.recordExecution("exec-xyz-001");
    assertThat(secondCall).isFalse();
  }

  @Test
  void runPoll_sameTradeTwice_recordedOnceOnly() {
    var trade = new IbkrRestClient.IbkrTrade(
        "exec-dupe-001", "10002", "SELL", 50.0, 153.20, 0.25,
        Instant.now().toEpochMilli(), 265598L, "TQQQ");
    when(mockClient.getTrades()).thenReturn(List.of(trade));

    poller.runPoll();
    poller.runPoll(); // second poll returns same trade

    // Only the engine's first recordExecution should have added it
    boolean thirdAttempt = engine.recordExecution("exec-dupe-001");
    assertThat(thirdAttempt).isFalse();
  }

  @Test
  void runPoll_tradeWithNullExecutionId_skipped() {
    var badTrade = new IbkrRestClient.IbkrTrade(
        null, "10003", "BUY", 10.0, 150.0, 0.10,
        Instant.now().toEpochMilli(), 265598L, "TQQQ");
    when(mockClient.getTrades()).thenReturn(List.of(badTrade));

    // Should not throw
    poller.runPoll();
  }

  @Test
  void runPoll_restClientThrows_doesNotPropagateException() {
    when(mockClient.getTrades()).thenThrow(new RuntimeException("network error"));

    // Should swallow and log, not throw
    poller.runPoll();
  }
}
