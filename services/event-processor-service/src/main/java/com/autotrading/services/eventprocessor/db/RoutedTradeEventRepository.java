package com.autotrading.services.eventprocessor.db;

import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface RoutedTradeEventRepository extends ListCrudRepository<RoutedTradeEventEntity, String> {

  Optional<RoutedTradeEventEntity> findByEventId(String eventId);

  boolean existsByClientEventId(String clientEventId);
}
