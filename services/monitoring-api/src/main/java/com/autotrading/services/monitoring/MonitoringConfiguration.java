package com.autotrading.services.monitoring;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.autotrading.services.monitoring.db")
@EntityScan(basePackages = "com.autotrading.services.monitoring.db")
public class MonitoringConfiguration {
  // IngressForwarder registered via @Component on InMemoryIngressForwarder
}
