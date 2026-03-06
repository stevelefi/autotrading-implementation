package com.autotrading.services.risk.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.autotrading.command.v1.OrderCommandServiceGrpc;
import com.autotrading.libs.observability.GrpcCorrelationServerInterceptor;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.services.risk.core.SimplePolicyEngine;
import com.autotrading.services.risk.grpc.RiskDecisionGrpcService;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
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
  OrderCommandServiceGrpc.OrderCommandServiceBlockingStub orderCommandStub(ManagedChannel orderGrpcChannel) {
    return OrderCommandServiceGrpc.newBlockingStub(orderGrpcChannel);
  }

  @Bean
  SimplePolicyEngine simplePolicyEngine() {
    return new SimplePolicyEngine();
  }

  @Bean
  RiskDecisionGrpcService riskDecisionGrpcService(
      OrderCommandServiceGrpc.OrderCommandServiceBlockingStub orderStub,
      SimplePolicyEngine policyEngine) {
    return new RiskDecisionGrpcService(orderStub, policyEngine);
  }

  @Bean
  RiskGrpcServerLifecycle riskGrpcServerLifecycle(
      RiskDecisionGrpcService grpcService,
      GrpcCorrelationServerInterceptor correlationInterceptor,
      @Value("${grpc.server.port:9091}") int grpcPort) {
    return new RiskGrpcServerLifecycle(grpcService, correlationInterceptor, grpcPort);
  }
}
