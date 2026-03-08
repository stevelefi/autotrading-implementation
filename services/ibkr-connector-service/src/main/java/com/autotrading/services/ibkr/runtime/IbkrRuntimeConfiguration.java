package com.autotrading.services.ibkr.runtime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.autotrading.libs.idempotency.IdempotencyService;
import com.autotrading.libs.kafka.DirectKafkaPublisher;
import com.autotrading.libs.observability.GrpcCorrelationServerInterceptor;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import com.autotrading.services.ibkr.db.BrokerOrderRepository;
import com.autotrading.services.ibkr.db.ExecutionRepository;
import com.autotrading.services.ibkr.grpc.BrokerCommandGrpcService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableTransactionManagement
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
      @Qualifier("bestEffortKafkaPublisher") DirectKafkaPublisher bestEffortPublisher,
      ObjectMapper objectMapper) {
    return new BrokerConnectorEngine(idempotencyService, brokerOrderRepository, executionRepository, bestEffortPublisher, objectMapper);
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

