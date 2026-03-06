package com.autotrading.services.performance;

import com.autotrading.services.performance.core.PerformanceProjectionService;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.autotrading.services.performance.db")
@EntityScan(basePackages = "com.autotrading.services.performance.db")
public class PerformanceConfiguration {

  @Bean
  public PerformanceProjectionService performanceProjectionService() {
    return new PerformanceProjectionService();
  }
}
