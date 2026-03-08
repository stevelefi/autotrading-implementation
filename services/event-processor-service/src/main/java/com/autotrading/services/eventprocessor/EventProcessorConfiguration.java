package com.autotrading.services.eventprocessor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.autotrading.services.eventprocessor.core.EventProcessorRouter;

@Configuration
@EnableTransactionManagement
public class EventProcessorConfiguration {

  @Bean
  public EventProcessorRouter eventProcessorRouter() {
    return new EventProcessorRouter();
  }
}
