package com.autotrading.libs.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Spring Boot auto-configuration that registers observability infrastructure
 * beans for any service that has {@code reliability-core} on the classpath.
 */
@AutoConfiguration
public class ReliabilityCoreAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public GrpcCorrelationServerInterceptor grpcCorrelationServerInterceptor() {
    return new GrpcCorrelationServerInterceptor();
  }

  @Bean
  @ConditionalOnMissingBean(HttpCorrelationFilter.class)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  public FilterRegistrationBean<HttpCorrelationFilter> httpCorrelationFilter() {
    FilterRegistrationBean<HttpCorrelationFilter> registration =
        new FilterRegistrationBean<>(new HttpCorrelationFilter());
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
