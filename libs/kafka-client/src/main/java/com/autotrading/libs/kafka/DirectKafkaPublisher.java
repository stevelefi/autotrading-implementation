package com.autotrading.libs.kafka;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Shared Kafka producer SDK wrapping the raw {@code kafka-clients} {@link KafkaProducer}.
 *
 * <h3>Retry behaviour</h3>
 * <p>Each call to {@link #publish} attempts the send up to a configurable total budget
 * (default 5 000 ms). Failed attempts are separated by a doubling back-off starting at
 * 10 ms (10 → 20 → 40 → 80 → … ms), capped at the remaining budget:
 *
 * <pre>
 *   attempt 0 → sleep  10 ms → attempt 1 → sleep  20 ms → … → budget exhausted → throw
 * </pre>
 *
 * <h3>MDC enrichment</h3>
 * <p>Sets {@code kafka_topic} and {@code kafka_attempt} in the SLF4J MDC for the duration
 * of each attempt so that retry details appear in structured log output.
 *
 * <h3>Thread safety</h3>
 * <p>{@link KafkaProducer} is thread-safe; this class can be shared across threads.
 *
 * <h3>Variants</h3>
 * <ul>
 *   <li>{@link #publish} — throws {@link KafkaPublishException} on exhaustion (hot path)
 *   <li>{@link #publishBestEffort} — logs WARN and swallows on exhaustion (audit / status events)
 * </ul>
 */
public class DirectKafkaPublisher {

  private static final Logger log = LoggerFactory.getLogger(DirectKafkaPublisher.class);

  /** Total time budget across all attempts (including sleep between them). */
  static final long TOTAL_PUBLISH_BUDGET_MS = 5_000L;
  /** First sleep interval in ms; doubles on each failure. */
  static final long INITIAL_SLEEP_MS = 10L;

  private final Producer<String, String> producer;
  private final long singleAttemptTimeoutMs;
  private final long totalPublishBudgetMs;

  /**
   * Production constructor. Creates an owned {@link KafkaProducer} from {@code producerProps}.
   *
   * @param producerProps  fully-configured producer properties
   * @param singleAttemptTimeoutMs per-attempt send timeout in milliseconds
   */
  public DirectKafkaPublisher(Properties producerProps, long singleAttemptTimeoutMs) {
    this.producer = new KafkaProducer<>(producerProps);
    this.singleAttemptTimeoutMs = singleAttemptTimeoutMs;
    this.totalPublishBudgetMs = TOTAL_PUBLISH_BUDGET_MS;
  }

  /**
   * Package-private constructor for unit tests — accepts a pre-built (mocked) producer.
   */
  DirectKafkaPublisher(Producer<String, String> producer, long singleAttemptTimeoutMs) {
    this.producer = producer;
    this.singleAttemptTimeoutMs = singleAttemptTimeoutMs;
    this.totalPublishBudgetMs = TOTAL_PUBLISH_BUDGET_MS;
  }

  /**
   * Package-private constructor for unit tests — allows overriding the total budget.
   */
  DirectKafkaPublisher(Producer<String, String> producer, long singleAttemptTimeoutMs, long totalBudgetMs) {
    this.producer = producer;
    this.singleAttemptTimeoutMs = singleAttemptTimeoutMs;
    this.totalPublishBudgetMs = totalBudgetMs;
  }

  /**
   * Publishes {@code payload} to {@code topic} with doubling-backoff retry up to
   * {@value #TOTAL_PUBLISH_BUDGET_MS} ms total.
   *
   * @throws KafkaPublishException if all retries are exhausted or the thread is interrupted
   */
  public void publish(String topic, String partitionKey, String payload) {
    long startMs = System.currentTimeMillis();
    long sleepMs = INITIAL_SLEEP_MS;
    int attempt = 0;
    Exception lastException = null;

    MDC.put("kafka_topic", topic);
    try {
      while (true) {
        MDC.put("kafka_attempt", String.valueOf(attempt));
        try {
          long attemptStart = System.currentTimeMillis();
          producer.send(new ProducerRecord<>(topic, partitionKey, payload))
              .get(singleAttemptTimeoutMs, TimeUnit.MILLISECONDS);
          long durationMs = System.currentTimeMillis() - attemptStart;
          log.info("kafka.publish.ok topic={} key={} attempt={} durationMs={}",
              topic, partitionKey, attempt, durationMs);
          return;

        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new KafkaPublishException(topic, partitionKey, e);

        } catch (TimeoutException | ExecutionException e) {
          lastException = e;
          long elapsed = System.currentTimeMillis() - startMs;
          long remaining = totalPublishBudgetMs - elapsed;
          long actualSleep = Math.max(0, Math.min(sleepMs, remaining));

          log.warn("kafka.publish.attempt.fail topic={} key={} attempt={} error={} nextRetryMs={} elapsedMs={}",
              topic, partitionKey, attempt, e.getMessage(), actualSleep, elapsed);

          if (remaining <= 0) {
            break;  // budget exhausted before sleeping — give up immediately
          }
          try {
            Thread.sleep(actualSleep);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException(topic, partitionKey, ie);
          }
          attempt++;
          sleepMs = Math.min(sleepMs * 2, totalPublishBudgetMs);
        } finally {
          MDC.remove("kafka_attempt");
        }
      }
    } finally {
      MDC.remove("kafka_topic");
    }

    long totalMs = System.currentTimeMillis() - startMs;
    log.error("kafka.publish.exhausted topic={} key={} totalAttempts={} totalElapsedMs={}",
        topic, partitionKey, attempt + 1, totalMs);
    throw new KafkaPublishException(topic, partitionKey, lastException);
  }

  /**
   * Best-effort variant: publishes the same way as {@link #publish} but catches and logs
   * {@link KafkaPublishException} instead of propagating it.
   * <p>Use for non-critical paths where a lost event is acceptable (e.g. audit, status).
   */
  public void publishBestEffort(String topic, String partitionKey, String payload) {
    try {
      publish(topic, partitionKey, payload);
    } catch (KafkaPublishException e) {
      log.warn("kafka.publish.best_effort.dropped topic={} key={} cause={}",
          topic, partitionKey, e.getMessage());
    }
  }

  @PreDestroy
  public void close() {
    try {
      producer.flush();
      producer.close(Duration.ofSeconds(5));
      log.info("DirectKafkaPublisher closed");
    } catch (Exception e) {
      log.warn("DirectKafkaPublisher close error: {}", e.getMessage());
    }
  }
}
