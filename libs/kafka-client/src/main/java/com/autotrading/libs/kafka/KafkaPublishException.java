package com.autotrading.libs.kafka;

/**
 * Unchecked exception thrown by {@link DirectKafkaPublisher} when a Kafka publish attempt
 * exhausts its retry budget or is interrupted.
 */
public class KafkaPublishException extends RuntimeException {

  public KafkaPublishException(String topic, String partitionKey, Throwable cause) {
    super("Kafka publish failed: topic=" + topic + " key=" + partitionKey
        + " — " + (cause != null ? cause.getMessage() : "unknown"), cause);
  }
}
