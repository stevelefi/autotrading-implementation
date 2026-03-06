package com.autotrading.services.ibkr.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.autotrading.libs.idempotency.IdempotencyService;
import com.autotrading.libs.observability.GrpcCorrelationServerInterceptor;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.libs.reliability.outbox.OutboxRepository;
import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import com.autotrading.services.ibkr.db.BrokerOrderRepository;
import com.autotrading.services.ibkr.db.ExecutionRepository;
import com.autotrading.services.ibkr.grpc.BrokerCommandGrpcService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.autotrading.services.ibkr.db")
@EntityScan(basePackages = "com.autotrading.services.ibkr.db")
public class IbkrRuntimeConfiguration {

  @Bean
  ReliabilityMetrics reliabilityMetrics(MeterRegistry meterRegistry) {
    return new ReliabilityMetrics(meterRegistry);
  }

  @Bean
  BrokerConnectorEngine brokerConnectorEngine(
      IdempotencyService idempotencyService,
      BrokerOrderRepository brokerOrderRepository,
      ExecutionRepository executionRepository,
      OutboxRepository outboxRepository,
      ObjectMapper objectMapper) {
    return new BrokerConnectorEngine(idempotencyService, brokerOrderRepository, executionRepository, outboxRepository, objectMapper);
  }

  @Bean
  BrokerCommandGrpcService brokerCommandGrpcService(BrokerConnectorEngine engine) {
    return new BrokerCommandGrpcService(engine);
  }

  @Bean
  IbkrGrpcServerLifecycle ibkrGrpcServerLifecycle(
      BrokerCommandGrpcService grpcService,
      GrpcCorrelationServerInterceptor correlationInterceptor,
      @Value("${grpc.server.port:9093}") int grpcPort) {
    return new IbkrGrpcServerLifecycle(grpcService, correlationInterceptor, grpcPort);
  }
}

