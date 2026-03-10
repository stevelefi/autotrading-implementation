package com.autotrading.services.ingress;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.autotrading.libs.auth.ApiKeyAuthenticator;
import com.autotrading.libs.auth.AuthenticatedPrincipal;
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

import io.micrometer.tracing.Tracer;

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
      entityStore.put(e.getClientEventId(), e);
      return e;
    });
    when(mockRawRepo.findByClientEventId(any())).thenAnswer(inv ->
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

    assertThat(first.event_id()).isEqualTo(second.event_id());
    verify(mockKafkaFirstPublisher, times(1)).publish(any(), any(), any());
  }

  @Test
  void sameKeyDifferentPayloadAlsoReturnsReplay() {
    IngressAcceptedResponse first = service.accept(new IngressSubmitRequest(
        "idemp-1",
        "TRADE_SIGNAL",
        "agent-1",
        null,
        null,
        Map.of("side", "BUY", "qty", 10)), "req-1", "Bearer token");
    simulateCommit();

    IngressAcceptedResponse second = service.accept(new IngressSubmitRequest(
        "idemp-1",
        "TRADE_SIGNAL",
        "agent-1",
        null,
        null,
        Map.of("side", "BUY", "qty", 20)), "req-2", "Bearer token");
    simulateCommit();

    assertThat(second.event_id()).isEqualTo(first.event_id());
    verify(mockKafkaFirstPublisher, times(1)).publish(any(), any(), any());
  }

  @Test
  void brokerDownReturns503() {
    // Build a service wired with a "broker is DOWN" health supplier
    IngressRawEventRepository stubRepo = mock(IngressRawEventRepository.class);
    IngressService brokerDownService = new IngressService(
        new InMemoryIdempotencyService(),
        mockKafkaFirstPublisher,
        stubRepo,
        new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
        mock(Tracer.class),
        () -> false,
        null /* no auth in test */);

    IngressSubmitRequest request = new IngressSubmitRequest(
        "idemp-broker-down",
        "TRADE_SIGNAL",
        "agent-1",
        null,
        null,
        Map.of("side", "BUY", "qty", 10));

    assertThatThrownBy(() -> brokerDownService.accept(request, "req-down", "Bearer token"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(t -> ((ResponseStatusException) t).getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void unknownApiKeyThrows401() {
    ApiKeyAuthenticator mockAuth = mock(ApiKeyAuthenticator.class);
    when(mockAuth.authenticate(any())).thenReturn(Optional.empty());
    IngressService svc = serviceWithAuth(mockAuth);

    assertThatThrownBy(() -> svc.accept(tradeSignalRequest("k-401"), "req-x", "Bearer unknown-key"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(t -> ((ResponseStatusException) t).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void agentNotOwnedByAccountThrows403() {
    AuthenticatedPrincipal principal = new AuthenticatedPrincipal("acc-other", "hash", 1);
    ApiKeyAuthenticator mockAuth = mock(ApiKeyAuthenticator.class);
    when(mockAuth.authenticate(any())).thenReturn(Optional.of(principal));
    when(mockAuth.isAgentOwnedBy("agent-1", "acc-other")).thenReturn(false);
    IngressService svc = serviceWithAuth(mockAuth);

    assertThatThrownBy(() -> svc.accept(tradeSignalRequest("k-403"), "req-x", "Bearer valid-key"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(t -> ((ResponseStatusException) t).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void missingBearerPrefixThrows400() {
    ApiKeyAuthenticator mockAuth = mock(ApiKeyAuthenticator.class);
    IngressService svc = serviceWithAuth(mockAuth);

    assertThatThrownBy(() -> svc.accept(tradeSignalRequest("k-400"), "req-x", "notbearer"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(t -> ((ResponseStatusException) t).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void validKeyAndOwnedAgentReturns202() {
    AuthenticatedPrincipal principal = new AuthenticatedPrincipal("acc-ok", "hash", 1);
    ApiKeyAuthenticator mockAuth = mock(ApiKeyAuthenticator.class);
    when(mockAuth.authenticate(any())).thenReturn(Optional.of(principal));
    when(mockAuth.isAgentOwnedBy("agent-1", "acc-ok")).thenReturn(true);
    IngressRawEventRepository stubRepo = mock(IngressRawEventRepository.class);
    when(stubRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(stubRepo.findByClientEventId(any())).thenReturn(Optional.empty());

    IngressService svc = new IngressService(
        new InMemoryIdempotencyService(),
        mockKafkaFirstPublisher,
        stubRepo,
        new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
        mock(Tracer.class),
        () -> true,
        mockAuth);

    IngressAcceptedResponse response = svc.accept(tradeSignalRequest("k-202"), "req-x", "Bearer good-key");
    assertThat(response.event_id()).isNotBlank();
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private IngressService serviceWithAuth(ApiKeyAuthenticator mockAuth) {
    IngressRawEventRepository stubRepo = mock(IngressRawEventRepository.class);
    when(stubRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(stubRepo.findByClientEventId(any())).thenReturn(Optional.empty());
    return new IngressService(
        new InMemoryIdempotencyService(),
        mockKafkaFirstPublisher,
        stubRepo,
        new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
        mock(Tracer.class),
        () -> true,
        mockAuth);
  }

  private static IngressSubmitRequest tradeSignalRequest(String clientEventId) {
    return new IngressSubmitRequest(
        clientEventId, "TRADE_SIGNAL", "agent-1", null, null,
        Map.of("side", "BUY", "qty", 10));
  }
}
