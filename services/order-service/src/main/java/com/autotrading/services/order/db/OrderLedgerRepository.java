package com.autotrading.services.order.db;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderLedgerRepository extends JpaRepository<OrderLedgerEntity, String> {}
