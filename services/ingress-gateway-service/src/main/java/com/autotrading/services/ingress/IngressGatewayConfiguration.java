package com.autotrading.services.ingress;

import java.util.function.BooleanSupplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.autotrading.libs.health.BrokerHealthCache;

@Configuration
public class IngressGatewayConfiguration {

  /**
   * Polls {@code broker_health_status} every 15 s (configurable via
   * {@code broker.health.cache.refresh-interval-ms}) and caches the result.
   * Starts at {@code SmartLifecycle} phase 50 — before Tomcat accepts connections.
   */
  @Bean
  BrokerHealthCache brokerHealthCache(
      JdbcTemplate jdbcTemplate,
      @Value("${broker.health.cache.refresh-interval-ms:15000}") long refreshIntervalMs) {
    return new BrokerHealthCache(jdbcTemplate, refreshIntervalMs);
  }

  /**
   * Single {@link BooleanSupplier} bean wired into {@link com.autotrading.services.ingress.core.IngressService}
   * so that the service avoids a direct import of the health infrastructure class.
   */
  @Bean
  BooleanSupplier brokerHealthGate(BrokerHealthCache brokerHealthCache) {
    return brokerHealthCache::isBrokerAvailable;
  }
}
