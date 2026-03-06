package com.autotrading.services.order.runtime;

import com.autotrading.command.v1.BrokerCommandServiceGrpc;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.services.order.core.OrderSafetyEngine;
import com.autotrading.services.order.grpc.OrderCommandGrpcService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderRuntimeConfiguration {

  @Bean
  ReliabilityMetrics reliabilityMetrics() {
    return new ReliabilityMetrics();
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean(destroyMethod = "shutdownNow")
  ManagedChannel brokerGrpcChannel(
      @Value("${broker.grpc.host:ibkr-connector-service}") String host,
      @Value("${broker.grpc.port:9093}") int port) {
    return ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
  }

  @Bean
  BrokerCommandServiceGrpc.BrokerCommandServiceBlockingStub brokerCommandStub(ManagedChannel brokerGrpcChannel) {
    return BrokerCommandServiceGrpc.newBlockingStub(brokerGrpcChannel);
  }

  @Bean
  OrderSafetyEngine orderSafetyEngine(ReliabilityMetrics reliabilityMetrics, Clock clock) {
    return new OrderSafetyEngine(reliabilityMetrics, clock);
  }

  @Bean
  OrderCommandGrpcService orderCommandGrpcService(
      OrderSafetyEngine orderSafetyEngine,
      BrokerCommandServiceGrpc.BrokerCommandServiceBlockingStub brokerStub) {
    return new OrderCommandGrpcService(orderSafetyEngine, brokerStub);
  }

  @Bean
  OrderGrpcServerLifecycle orderGrpcServerLifecycle(
      OrderCommandGrpcService grpcService,
      @Value("${grpc.server.port:9092}") int grpcPort) {
    return new OrderGrpcServerLifecycle(grpcService, grpcPort);
  }
}
