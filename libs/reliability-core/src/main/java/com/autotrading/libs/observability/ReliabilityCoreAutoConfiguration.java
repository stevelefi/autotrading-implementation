package com.autotrading.libs.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;

import com.autotrading.libs.idempotency.IdempotencyService;
import com.autotrading.libs.idempotency.JdbcIdempotencyService;
import com.autotrading.libs.reliability.inbox.ConsumerDeduper;
import com.autotrading.libs.reliability.inbox.ConsumerInboxRepository;
import com.autotrading.libs.reliability.inbox.JdbcConsumerInboxRepository;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.libs.reliability.outbox.JdbcOutboxRepository;
import com.autotrading.libs.reliability.outbox.OutboxDispatcher;
import com.autotrading.libs.reliability.outbox.OutboxPollerLifecycle;
import com.autotrading.libs.reliability.outbox.OutboxPublisher;
import com.autotrading.libs.reliability.outbox.OutboxRepository;
import com.autotrading.libs.reliability.outbox.TransactionalOutboxExecutor;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Spring Boot auto-configuration for the reliability-core library.
 *
 * <p>Registers the following beans, each conditional on dependencies being present:
 * <ul>
 *   <li>Observability: {@link GrpcCorrelationServerInterceptor}, {@link HttpCorrelationFilter}
 *   <li>Metrics: {@link ReliabilityMetrics} (uses {@link MeterRegistry} when available)
 *   <li>JDBC persistence (requires {@link JdbcTemplate}):
 *       {@link JdbcOutboxRepository}, {@link JdbcConsumerInboxRepository}, {@link JdbcIdempotencyService}
 *   <li>Outbox relay (only when {@code autotrading.outbox.poller.enabled=true}, default true):
 *       {@link OutboxDispatcher}, {@link OutboxPollerLifecycle}
 *   <li>Helpers: {@link TransactionalOutboxExecutor}, {@link ConsumerDeduper}
 * </ul>
 *
 * <p>Kafka publisher beans ({@code KafkaOutboxPublisher}, {@code KafkaFirstPublisher}) are
 * provided by the {@code kafka-client} library auto-configuration, not here.
 * Non-ingress services should set {@code autotrading.outbox.poller.enabled=false} to suppress
 * the outbox poller.
 */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class)
public class ReliabilityCoreAutoConfiguration {

  // ── Observability ────────────────────────────────────────────────────────────

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

  // ── Metrics ──────────────────────────────────────────────────────────────────

  @Bean
  @ConditionalOnMissingBean
  public ReliabilityMetrics reliabilityMetrics(java.util.Optional<MeterRegistry> meterRegistry) {
    return meterRegistry.map(ReliabilityMetrics::new).orElseGet(ReliabilityMetrics::new);
  }

  // ── JDBC persistence ─────────────────────────────────────────────────────────

  @Bean
  @ConditionalOnMissingBean(OutboxRepository.class)
  @ConditionalOnBean(JdbcTemplate.class)
  public JdbcOutboxRepository jdbcOutboxRepository(JdbcTemplate jdbc) {
    return new JdbcOutboxRepository(jdbc);
  }

  @Bean
  @ConditionalOnMissingBean(ConsumerInboxRepository.class)
  @ConditionalOnBean(JdbcTemplate.class)
  public JdbcConsumerInboxRepository jdbcConsumerInboxRepository(JdbcTemplate jdbc) {
    return new JdbcConsumerInboxRepository(jdbc);
  }

  @Bean
  @ConditionalOnMissingBean(IdempotencyService.class)
  @ConditionalOnBean(JdbcTemplate.class)
  public JdbcIdempotencyService jdbcIdempotencyService(JdbcTemplate jdbc) {
    return new JdbcIdempotencyService(jdbc);
  }


  // ── Outbox relay (opt-in; enabled by default, disabled in non-ingress services) ─────────────

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean({OutboxRepository.class, OutboxPublisher.class})
  @ConditionalOnProperty(name = "autotrading.outbox.poller.enabled", havingValue = "true", matchIfMissing = true)
  public OutboxDispatcher outboxDispatcher(
      OutboxRepository outboxRepository,
      OutboxPublisher publisher,
      ReliabilityMetrics metrics) {
    return new OutboxDispatcher(outboxRepository, publisher, metrics);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(OutboxDispatcher.class)
  @ConditionalOnProperty(name = "autotrading.outbox.poller.enabled", havingValue = "true", matchIfMissing = true)
  public OutboxPollerLifecycle outboxPollerLifecycle(OutboxDispatcher outboxDispatcher) {
    return new OutboxPollerLifecycle(outboxDispatcher);
  }


  // ── Application-layer helpers ────────────────────────────────────────────────

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(OutboxRepository.class)
  @Deprecated
  public TransactionalOutboxExecutor transactionalOutboxExecutor(OutboxRepository outboxRepository) {
    return new TransactionalOutboxExecutor(outboxRepository);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(ConsumerInboxRepository.class)
  public ConsumerDeduper consumerDeduper(
      ConsumerInboxRepository consumerInboxRepository,
      ReliabilityMetrics metrics) {
    return new ConsumerDeduper(consumerInboxRepository, metrics);
  }
}
