package com.autotrading.services.ibkr.db;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BrokerOrderRepository extends JpaRepository<BrokerOrderEntity, String> {
  List<BrokerOrderEntity> findByOrderIntentId(String orderIntentId);
  Optional<BrokerOrderEntity> findByOrderRef(String orderRef);
}
