package com.autotrading.services.order.runtime;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.autotrading.command.v1.BrokerCommandServiceGrpc;
import com.autotrading.libs.idempotency.IdempotencyService;
import com.autotrading.libs.observability.GrpcCorrelationServerInterceptor;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.services.order.core.OrderSafetyEngine;
import com.autotrading.services.order.db.OrderIntentRepository;
import com.autotrading.services.order.db.OrderLedgerRepository;
import com.autotrading.services.order.grpc.OrderCommandGrpcService;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.autotrading.services.order.db")
@EntityScan(basePackages = "com.autotrading.services.order.db")
public class OrderRuntimeConfiguration {

  @Bean
  ReliabilityMetrics reliabilityMetrics(MeterRegistry meterRegistry) {
    return new ReliabilityMetrics(meterRegistry);
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
  OrderSafetyEngine orderSafetyEngine(ReliabilityMetrics reliabilityMetrics, Clock clock,
                                       IdempotencyService idempotencyService,
                                       OrderIntentRepository orderIntentRepository,
                                       OrderLedgerRepository orderLedgerRepository) {
    return new OrderSafetyEngine(reliabilityMetrics, clock, idempotencyService,
        orderIntentRepository, orderLedgerRepository);
  }

  @Bean
  OrderTimeoutWatchdogLifecycle orderTimeoutWatchdogLifecycle(
      OrderSafetyEngine orderSafetyEngine,
      Clock clock,
      @Value("${order.timeout.watchdog.interval.ms:5000}") long intervalMs) {
    return new OrderTimeoutWatchdogLifecycle(orderSafetyEngine, clock, intervalMs);
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
      GrpcCorrelationServerInterceptor correlationInterceptor,
      @Value("${grpc.server.port:9092}") int grpcPort) {
    return new OrderGrpcServerLifecycle(grpcService, correlationInterceptor, grpcPort);
  }
}

