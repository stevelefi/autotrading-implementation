package com.autotrading.services.order.db;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStateHistoryRepository
    extends JpaRepository<OrderStateHistoryEntity, OrderStateHistoryEntity.OrderStateHistoryId> {
  List<OrderStateHistoryEntity> findByIdOrderIntentIdOrderByIdSequenceNoAsc(String orderIntentId);
}
