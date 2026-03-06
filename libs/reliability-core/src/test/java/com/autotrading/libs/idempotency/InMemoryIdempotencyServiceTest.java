package com.autotrading.libs.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryIdempotencyServiceTest {

  @Test
  void sameKeySamePayloadReturnsReplay() {
    InMemoryIdempotencyService service = new InMemoryIdempotencyService();

    ClaimResult first = service.claim(new IdempotencyClaim("k1", "payload-a", Instant.now()));
    service.markCompleted("k1", "accepted-response");
    ClaimResult second = service.claim(new IdempotencyClaim("k1", "payload-a", Instant.now()));

    assertThat(first.outcome()).isEqualTo(ClaimOutcome.CLAIMED);
    assertThat(second.outcome()).isEqualTo(ClaimOutcome.REPLAY);
    assertThat(second.record().status()).isEqualTo(IdempotencyStatus.COMPLETED);
    assertThat(second.record().responseSnapshot()).isEqualTo("accepted-response");
  }

  @Test
  void sameKeyDifferentPayloadReturnsConflict() {
    InMemoryIdempotencyService service = new InMemoryIdempotencyService();

    service.claim(new IdempotencyClaim("k1", "payload-a", Instant.now()));
    ClaimResult conflict = service.claim(new IdempotencyClaim("k1", "payload-b", Instant.now()));

    assertThat(conflict.outcome()).isEqualTo(ClaimOutcome.CONFLICT);
  }
}
