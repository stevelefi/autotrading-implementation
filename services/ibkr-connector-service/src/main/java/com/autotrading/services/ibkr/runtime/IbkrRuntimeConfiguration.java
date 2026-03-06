package com.autotrading.services.ibkr.runtime;

import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import com.autotrading.services.ibkr.grpc.BrokerCommandGrpcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IbkrRuntimeConfiguration {

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
      @Value("${grpc.server.port:9093}") int grpcPort) {
    return new IbkrGrpcServerLifecycle(grpcService, grpcPort);
  }
}
