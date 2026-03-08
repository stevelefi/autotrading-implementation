package com.autotrading.libs.kafka;

import java.util.Properties;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import com.autotrading.libs.observability.ReliabilityCoreAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.autotrading.libs.reliability.outbox.OutboxPublisher;
import com.autotrading.libs.reliability.outbox.OutboxRepository;

/**
 * Spring Boot auto-configuration for the kafka-client library.
 *
 * <p>Registers:
 * <ul>
 *   <li>{@code directKafkaPublisher} — primary producer (acks=all), used for critical paths
 *   <li>{@code bestEffortKafkaPublisher} — secondary producer (acks=1), used for audit/status events
 *   <li>{@link KafkaOutboxPublisher} — routes outbox relay through {@code directKafkaPublisher}
 *   <li>{@code outboxFallbackTxTemplate} — REQUIRES_NEW TX template (ingress only)
 *   <li>{@link KafkaFirstPublisher} — Kafka-first + outbox fallback (ingress only)
 * </ul>
 *
 * <p>Producer config is tuned for low-latency single-record publishes:
 * {@code linger.ms=0}, {@code batch.size=1}, {@code retries=0} (retry logic is owned by
 * {@link DirectKafkaPublisher}'s doubling-backoff loop), {@code enable.idempotence=false}.
 */
@AutoConfiguration(after = ReliabilityCoreAutoConfiguration.class)
public class KafkaClientAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(KafkaClientAutoConfiguration.class);

  @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
  private String bootstrapServers;

  @Value("${autotrading.kafka.publish-timeout-ms:500}")
  private long publishTimeoutMs;

  // ── Primary producer (acks=all — reliability-critical path) ──────────────────

  @Bean("directKafkaPublisher")
  @ConditionalOnMissingBean(name = "directKafkaPublisher")
  public DirectKafkaPublisher directKafkaPublisher() {
    Properties props = baseProducerProps();
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    log.info("Creating directKafkaPublisher bootstrapServers={} timeoutMs={} acks=all",
        bootstrapServers, publishTimeoutMs);
    return new DirectKafkaPublisher(props, publishTimeoutMs);
  }

  // ── Secondary producer (acks=1 — best-effort audit / status events) ──────────

  @Bean("bestEffortKafkaPublisher")
  @ConditionalOnMissingBean(name = "bestEffortKafkaPublisher")
  public DirectKafkaPublisher bestEffortKafkaPublisher() {
    Properties props = baseProducerProps();
    props.put(ProducerConfig.ACKS_CONFIG, "1");
    log.info("Creating bestEffortKafkaPublisher bootstrapServers={} timeoutMs={} acks=1",
        bootstrapServers, publishTimeoutMs);
    return new DirectKafkaPublisher(props, publishTimeoutMs);
  }

  // ── Outbox relay publisher ────────────────────────────────────────────────────

  @Bean
  @ConditionalOnMissingBean(OutboxPublisher.class)
  @ConditionalOnBean(name = "directKafkaPublisher")
  public KafkaOutboxPublisher kafkaOutboxPublisher(
      @Qualifier("directKafkaPublisher") DirectKafkaPublisher directKafkaPublisher) {
    return new KafkaOutboxPublisher(directKafkaPublisher);
  }

  // ── Kafka-first fallback TX template (ingress-gateway-service only) ───────────

  @Bean("outboxFallbackTxTemplate")
  @ConditionalOnMissingBean(name = "outboxFallbackTxTemplate")
  @ConditionalOnBean({OutboxRepository.class})
  @ConditionalOnProperty(name = "autotrading.outbox.poller.enabled", havingValue = "true", matchIfMissing = true)
  public TransactionTemplate outboxFallbackTxTemplate(PlatformTransactionManager txManager) {
    TransactionTemplate t = new TransactionTemplate(txManager);
    t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return t;
  }

  @Bean
  @ConditionalOnMissingBean(KafkaFirstPublisher.class)
  @ConditionalOnBean(name = {"directKafkaPublisher", "outboxFallbackTxTemplate"})
  @ConditionalOnProperty(name = "autotrading.outbox.poller.enabled", havingValue = "true", matchIfMissing = true)
  public KafkaFirstPublisher kafkaFirstPublisher(
      @Qualifier("directKafkaPublisher") DirectKafkaPublisher directKafkaPublisher,
      OutboxRepository outboxRepository,
      @Qualifier("outboxFallbackTxTemplate") TransactionTemplate outboxFallbackTxTemplate) {
    return new KafkaFirstPublisher(directKafkaPublisher, outboxRepository, outboxFallbackTxTemplate);
  }

  // ── Internal helpers ──────────────────────────────────────────────────────────

  private Properties baseProducerProps() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    // Low-latency: no batching delay, no compression CPU overhead
    props.put(ProducerConfig.LINGER_MS_CONFIG, "0");
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none");
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, "1");
    // Timeouts: don't block the thread long waiting for metadata or buffer space
    props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "2000");
    props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "1000");
    // Allow internal delivery attempts (required before the SDK's outer retry)
    props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "10000");
    // Disable producer-internal retries — DirectKafkaPublisher owns the retry loop
    props.put(ProducerConfig.RETRIES_CONFIG, "0");
    // enable.idempotence requires retries >= 1; with retries=0 it must be false
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "false");
    return props;
  }
}
