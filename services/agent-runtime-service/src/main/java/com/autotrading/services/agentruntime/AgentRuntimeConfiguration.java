package com.autotrading.services.agentruntime;

import com.autotrading.command.v1.RiskDecisionServiceGrpc;
import com.autotrading.services.agentruntime.core.RoutedToSignalAdapter;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class AgentRuntimeConfiguration {

  @Bean(destroyMethod = "shutdownNow")
  public ManagedChannel riskServiceChannel(
      @Value("${risk.grpc.host:risk-service}") String host,
      @Value("${risk.grpc.port:9091}") int port) {
    return ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
  }

  @Bean
  public RiskDecisionServiceGrpc.RiskDecisionServiceBlockingStub riskDecisionStub(
      ManagedChannel riskServiceChannel) {
    return RiskDecisionServiceGrpc.newBlockingStub(riskServiceChannel);
  }

  @Bean
  public RoutedToSignalAdapter routedToSignalAdapter() {
    return new RoutedToSignalAdapter();
  }
}
