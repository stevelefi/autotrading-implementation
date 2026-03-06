package com.autotrading.services.eventprocessor;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.autotrading.services.eventprocessor.core.EventProcessorRouter;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.autotrading.services.eventprocessor.db")
@EntityScan(basePackages = "com.autotrading.services.eventprocessor.db")
public class EventProcessorConfiguration {

  @Bean
  public EventProcessorRouter eventProcessorRouter() {
    return new EventProcessorRouter();
  }
}
