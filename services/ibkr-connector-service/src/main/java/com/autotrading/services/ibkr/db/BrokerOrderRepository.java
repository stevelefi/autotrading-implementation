package com.autotrading.services.ibkr.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface BrokerOrderRepository extends ListCrudRepository<BrokerOrderEntity, String> {
  List<BrokerOrderEntity> findByOrderIntentId(String orderIntentId);
  Optional<BrokerOrderEntity> findByOrderRef(String orderRef);
}
