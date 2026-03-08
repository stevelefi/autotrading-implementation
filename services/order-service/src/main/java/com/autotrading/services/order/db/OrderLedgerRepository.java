package com.autotrading.services.order.db;

import org.springframework.data.repository.ListCrudRepository;

public interface OrderLedgerRepository extends ListCrudRepository<OrderLedgerEntity, String> {}
