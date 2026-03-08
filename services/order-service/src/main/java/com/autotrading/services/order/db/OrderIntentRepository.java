package com.autotrading.services.order.db;

import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface OrderIntentRepository extends ListCrudRepository<OrderIntentEntity, String> {
  Optional<OrderIntentEntity> findByIdempotencyKey(String idempotencyKey);
  boolean existsByIdempotencyKey(String idempotencyKey);
}
