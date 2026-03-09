package com.autotrading.services.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Tracer;

import com.autotrading.libs.idempotency.InMemoryIdempotencyService;
import com.autotrading.libs.kafka.KafkaFirstPublisher;
import com.autotrading.services.ingress.api.IngressAcceptedResponse;
import com.autotrading.services.ingress.api.IngressSubmitRequest;
import com.autotrading.services.ingress.core.IngressService;
import com.autotrading.services.ingress.db.IngressRawEventEntity;
import com.autotrading.services.ingress.db.IngressRawEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class IngressServiceTest {

  private KafkaFirstPublisher mockKafkaFirstPublisher;
  private IngressService service;

  @BeforeEach
  void setUp() {
    TransactionSynchronizationManager.initSynchronization();
    Map<String, IngressRawEventEntity> entityStore = new HashMap<>();
    mockKafkaFirstPublisher = mock(KafkaFirstPublisher.class);
    IngressRawEventRepository mockRawRepo = mock(IngressRawEventRepository.class);
    when(mockRawRepo.save(any())).thenAnswer(inv -> {
      IngressRawEventEntity e = inv.getArgument(0);
      entityStore.put(e.getIdempotencyKey(), e);
      return e;
    });
    when(mockRawRepo.findByIdempotencyKey(any())).thenAnswer(inv ->
        Optional.ofNullable(entityStore.get((String) inv.getArgument(0))));
    // Tracer mock returns null for currentSpan() — exercises the UUID fallback path
    service = new IngressService(
        new InMemoryIdempotencyService(),
        mockKafkaFirstPublisher,
        mockRawRepo,
        new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
        mock(Tracer.class));
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  /** Simulates a transaction commit: triggers afterCommit callbacks and reinitializes sync. */
  private void simulateCommit() {
    List<TransactionSynchronization> syncs =
        List.copyOf(TransactionSynchronizationManager.getSynchronizations());
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.initSynchronization();
    syncs.forEach(s -> {
      try { s.afterCommit(); } catch (Exception ignored) {}
    });
  }

  @Test
  void sameKeySamePayloadReturnsReplayedAcceptance() {
    IngressSubmitRequest request = new IngressSubmitRequest(
        "idemp-1",
        "TRADE_SIGNAL",
        "agent-1",
        null,
        null,
        Map.of("side", "BUY", "qty", 10));

    IngressAcceptedResponse first = service.accept(request, "req-1", "Bearer token");
    simulateCommit();

    IngressAcceptedResponse second = service.accept(request, "req-2", "Bearer token");
    simulateCommit();

    assertThat(first.data().ingress_event_id()).isEqualTo(second.data().ingress_event_id());
    verify(mockKafkaFirstPublisher, times(1)).publish(any(), any(), any());
  }

  @Test
  void sameKeyDifferentPayloadReturnsConflict() {
    service.accept(new IngressSubmitRequest(
        "idemp-1",
        "TRADE_SIGNAL",
        "agent-1",
        null,
        null,
        Map.of("side", "BUY", "qty", 10)), "req-1", "Bearer token");
    simulateCommit();

    assertThatThrownBy(() -> service.accept(new IngressSubmitRequest(
        "idemp-1",
        "TRADE_SIGNAL",
        "agent-1",
        null,
        null,
        Map.of("side", "BUY", "qty", 20)), "req-2", "Bearer token"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409 CONFLICT");
  }
}
