package com.autotrading.services.monitoring;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class MonitoringConfiguration {
  // IngressForwarder registered via @Component on InMemoryIngressForwarder
}
