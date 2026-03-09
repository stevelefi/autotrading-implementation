package com.autotrading.services.risk.runtime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.autotrading.command.v1.OrderCommandServiceGrpc;
import com.autotrading.libs.kafka.DirectKafkaPublisher;
import com.autotrading.libs.observability.GrpcCorrelationServerInterceptor;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.services.risk.core.SimplePolicyEngine;
import com.autotrading.services.risk.db.PolicyDecisionLogRepository;
import com.autotrading.services.risk.db.RiskDecisionRepository;
import com.autotrading.services.risk.grpc.RiskDecisionGrpcService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableTransactionManagement
public class RiskRuntimeConfiguration {

  @Bean
  ReliabilityMetrics reliabilityMetrics(MeterRegistry meterRegistry) {
    return new ReliabilityMetrics(meterRegistry);
  }

  @Bean(destroyMethod = "shutdownNow")
  ManagedChannel orderGrpcChannel(
      @Value("${order.grpc.host:order-service}") String host,
      @Value("${order.grpc.port:9092}") int port) {
    return ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
  }

  @Bean
  OrderCommandServiceGrpc.OrderCommandServiceFutureStub orderCommandStub(ManagedChannel orderGrpcChannel) {
    return OrderCommandServiceGrpc.newFutureStub(orderGrpcChannel);
  }

  @Bean
  SimplePolicyEngine simplePolicyEngine() {
    return new SimplePolicyEngine();
  }

  @Bean
  RiskDecisionGrpcService riskDecisionGrpcService(
      OrderCommandServiceGrpc.OrderCommandServiceFutureStub orderStub,
      SimplePolicyEngine policyEngine,
      RiskDecisionRepository riskDecisionRepository,
      PolicyDecisionLogRepository policyDecisionLogRepository,
      @Qualifier("bestEffortKafkaPublisher") DirectKafkaPublisher bestEffortPublisher,
      ObjectMapper objectMapper) {
    return new RiskDecisionGrpcService(orderStub, policyEngine, riskDecisionRepository,
        policyDecisionLogRepository, bestEffortPublisher, objectMapper);
  }

  @Bean
  RiskGrpcServerLifecycle riskGrpcServerLifecycle(
      RiskDecisionGrpcService grpcService,
      GrpcCorrelationServerInterceptor correlationInterceptor,
      @Value("${grpc.server.port:9091}") int grpcPort) {
    return new RiskGrpcServerLifecycle(grpcService, correlationInterceptor, grpcPort);
  }
}

