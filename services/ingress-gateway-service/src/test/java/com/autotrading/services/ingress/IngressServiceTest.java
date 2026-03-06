package com.autotrading.services.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autotrading.libs.idempotency.InMemoryIdempotencyService;
import com.autotrading.libs.reliability.outbox.OutboxRepository;
import com.autotrading.services.ingress.api.IngressAcceptedResponse;
import com.autotrading.services.ingress.api.IngressSubmitRequest;
import com.autotrading.services.ingress.core.IngressService;
import com.autotrading.services.ingress.db.IngressRawEventEntity;
import com.autotrading.services.ingress.db.IngressRawEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class IngressServiceTest {

  private OutboxRepository mockOutbox;
  private IngressService service;

  @BeforeEach
  void setUp() {
    Map<String, IngressRawEventEntity> entityStore = new HashMap<>();
    mockOutbox = mock(OutboxRepository.class);
    IngressRawEventRepository mockRawRepo = mock(IngressRawEventRepository.class);
    when(mockRawRepo.save(any())).thenAnswer(inv -> {
      IngressRawEventEntity e = inv.getArgument(0);
      entityStore.put(e.getIdempotencyKey(), e);
      return e;
    });
    when(mockRawRepo.findByIdempotencyKey(any())).thenAnswer(inv ->
        Optional.ofNullable(entityStore.get((String) inv.getArgument(0))));
    service = new IngressService(
        new InMemoryIdempotencyService(),
        mockOutbox,
        mockRawRepo,
        new ObjectMapper());
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
    IngressAcceptedResponse second = service.accept(request, "req-2", "Bearer token");

    assertThat(first.data().ingress_event_id()).isEqualTo(second.data().ingress_event_id());
    verify(mockOutbox, times(1)).append(any());
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
