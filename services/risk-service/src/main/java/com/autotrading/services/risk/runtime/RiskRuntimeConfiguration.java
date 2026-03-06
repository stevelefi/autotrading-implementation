package com.autotrading.services.risk.runtime;

import com.autotrading.command.v1.OrderCommandServiceGrpc;
import com.autotrading.services.risk.core.SimplePolicyEngine;
import com.autotrading.services.risk.grpc.RiskDecisionGrpcService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RiskRuntimeConfiguration {

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
      @Value("${grpc.server.port:9091}") int grpcPort) {
    return new RiskGrpcServerLifecycle(grpcService, grpcPort);
  }
}
