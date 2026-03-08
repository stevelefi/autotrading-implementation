package com.autotrading.services.ingress.db;

import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface IngressRawEventRepository extends ListCrudRepository<IngressRawEventEntity, String> {

  Optional<IngressRawEventEntity> findByIdempotencyKey(String idempotencyKey);

  boolean existsByIdempotencyKey(String idempotencyKey);
}
