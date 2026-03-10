package com.autotrading.services.order.db;

import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface OrderIntentRepository extends ListCrudRepository<OrderIntentEntity, String> {
  Optional<OrderIntentEntity> findByClientEventId(String clientEventId);
  boolean existsByClientEventId(String clientEventId);
}
