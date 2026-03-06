package com.autotrading.services.order.db;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderIntentRepository extends JpaRepository<OrderIntentEntity, String> {
  Optional<OrderIntentEntity> findByIdempotencyKey(String idempotencyKey);
  boolean existsByIdempotencyKey(String idempotencyKey);
}
