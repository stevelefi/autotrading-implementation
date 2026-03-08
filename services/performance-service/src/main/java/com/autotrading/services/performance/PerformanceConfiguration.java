package com.autotrading.services.performance;

import com.autotrading.services.performance.core.PerformanceProjectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class PerformanceConfiguration {

  @Bean
  public PerformanceProjectionService performanceProjectionService() {
    return new PerformanceProjectionService();
  }
}
