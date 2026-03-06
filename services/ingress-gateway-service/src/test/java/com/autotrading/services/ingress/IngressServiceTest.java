package com.autotrading.services.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autotrading.services.ingress.api.IngressAcceptedResponse;
import com.autotrading.services.ingress.api.IngressSubmitRequest;
import com.autotrading.services.ingress.core.IngressService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class IngressServiceTest {

  @Test
  void sameKeySamePayloadReturnsReplayedAcceptance() {
    IngressService service = new IngressService();

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
    assertThat(service.pendingOutboxCount()).isEqualTo(1);
  }

  @Test
  void sameKeyDifferentPayloadReturnsConflict() {
    IngressService service = new IngressService();

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
