package com.autotrading.services.ingress.db;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngressRawEventRepository extends JpaRepository<IngressRawEventEntity, String> {

  Optional<IngressRawEventEntity> findByIdempotencyKey(String idempotencyKey);

  boolean existsByIdempotencyKey(String idempotencyKey);
}
