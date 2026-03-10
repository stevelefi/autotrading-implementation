package com.autotrading.services.ibkr.runtime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.autotrading.libs.auth.BrokerAccountCache;
import com.autotrading.libs.idempotency.IdempotencyService;
import com.autotrading.libs.kafka.DirectKafkaPublisher;
import com.autotrading.libs.observability.GrpcCorrelationServerInterceptor;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.services.ibkr.client.IbkrHealthProbe;
import com.autotrading.services.ibkr.client.IbkrOrderPoller;
import com.autotrading.services.ibkr.client.IbkrRestClient;
import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import com.autotrading.services.ibkr.db.BrokerOrderRepository;
import com.autotrading.services.ibkr.db.ExecutionRepository;
import com.autotrading.services.ibkr.grpc.BrokerCommandGrpcService;
import com.autotrading.services.ibkr.health.BrokerHealthPersister;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableTransactionManagement
public class IbkrRuntimeConfiguration {

  @Value("${ibkr.mode:SIMULATOR}")
  private String ibkrMode;

  @Value("${ibkr.cp.base-url:http://localhost:8081}")
  private String cpBaseUrl;

  @Value("${ibkr.cp.account-id:DU123456}")
  private String cpAccountId;

  @Value("${ibkr.cp.tickle-interval-ms:30000}")
  private long tickleIntervalMs;

  @Value("${ibkr.cp.poll-interval-ms:5000}")
  private long pollIntervalMs;

  @Value("${broker.account.cache.refresh-interval-ms:60000}")
  private long brokerAccountCacheRefreshMs;

  @Bean
  ReliabilityMetrics reliabilityMetrics(MeterRegistry meterRegistry) {
    return new ReliabilityMetrics(meterRegistry);
  }

  @Bean
  BrokerAccountCache brokerAccountCache(JdbcTemplate jdbcTemplate) {
    return new BrokerAccountCache(jdbcTemplate, brokerAccountCacheRefreshMs, cpAccountId);
  }

  @Bean
  IbkrRestClient ibkrRestClient(BrokerAccountCache brokerAccountCache) {
    return new IbkrRestClient(cpBaseUrl, brokerAccountCache::resolveOrDefault);
  }

  @Bean
  BrokerHealthPersister brokerHealthPersister(NamedParameterJdbcTemplate namedJdbc) {
    return new BrokerHealthPersister(namedJdbc);
  }

  @Bean
  IbkrHealthProbe ibkrHealthProbe(IbkrRestClient ibkrRestClient,
                                   BrokerHealthPersister brokerHealthPersister) {
    boolean simulatorMode = "SIMULATOR".equalsIgnoreCase(ibkrMode);
    return new IbkrHealthProbe(ibkrRestClient, tickleIntervalMs, simulatorMode,
        brokerHealthPersister::onTransition);
  }

  @Bean
  BrokerConnectorEngine brokerConnectorEngine(
      IdempotencyService idempotencyService,
      BrokerOrderRepository brokerOrderRepository,
      ExecutionRepository executionRepository,
      @Qualifier("bestEffortKafkaPublisher") DirectKafkaPublisher bestEffortPublisher,
      ObjectMapper objectMapper,
      IbkrRestClient ibkrRestClient,
      IbkrHealthProbe ibkrHealthProbe) {
    boolean simulatorMode = "SIMULATOR".equalsIgnoreCase(ibkrMode);
    return new BrokerConnectorEngine(
        idempotencyService, brokerOrderRepository, executionRepository,
        bestEffortPublisher, objectMapper,
        ibkrHealthProbe, ibkrRestClient, simulatorMode);
  }

  @Bean
  IbkrOrderPoller ibkrOrderPoller(IbkrRestClient ibkrRestClient,
                                   BrokerConnectorEngine engine,
                                   IbkrHealthProbe ibkrHealthProbe) {
    return new IbkrOrderPoller(ibkrRestClient, engine, ibkrHealthProbe, pollIntervalMs);
  }

  @Bean
  BrokerCommandGrpcService brokerCommandGrpcService(BrokerConnectorEngine engine) {
    return new BrokerCommandGrpcService(engine);
  }

  @Bean
  IbkrGrpcServerLifecycle ibkrGrpcServerLifecycle(
      BrokerCommandGrpcService grpcService,
      GrpcCorrelationServerInterceptor correlationInterceptor,
      @Value("${grpc.server.port:9090}") int grpcPort) {
    return new IbkrGrpcServerLifecycle(grpcService, correlationInterceptor, grpcPort);
  }
}

