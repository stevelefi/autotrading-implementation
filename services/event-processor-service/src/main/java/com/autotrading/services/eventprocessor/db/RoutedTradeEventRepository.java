package com.autotrading.services.eventprocessor.db;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutedTradeEventRepository extends JpaRepository<RoutedTradeEventEntity, String> {

  Optional<RoutedTradeEventEntity> findByIngressEventId(String ingressEventId);

  boolean existsByIdempotencyKey(String idempotencyKey);
}
