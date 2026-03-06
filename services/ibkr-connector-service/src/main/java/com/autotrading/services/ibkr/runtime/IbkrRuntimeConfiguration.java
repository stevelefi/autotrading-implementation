package com.autotrading.services.ibkr.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.autotrading.libs.observability.GrpcCorrelationServerInterceptor;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import com.autotrading.services.ibkr.grpc.BrokerCommandGrpcService;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class IbkrRuntimeConfiguration {

  @Bean
  ReliabilityMetrics reliabilityMetrics(MeterRegistry meterRegistry) {
    return new ReliabilityMetrics(meterRegistry);
  }

  @Bean
  BrokerConnectorEngine brokerConnectorEngine() {
    return new BrokerConnectorEngine();
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
