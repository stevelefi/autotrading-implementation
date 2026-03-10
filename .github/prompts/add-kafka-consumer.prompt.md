# Adding a New Kafka Consumer

Use this prompt when you need to add a new `@KafkaListener` consumer to an existing service.

---

## Pattern Overview

All consumers in this project follow the **consumer inbox / dedup** pattern:

```
@KafkaListener
    │
    └─ ConsumerDeduper.process(consumerId, messageId, () -> {
           // business logic in here — runs inside the SAME DB transaction
           // write domain records, update state, call gRPC, etc.
           // Kafka offset is committed ONLY after DB TX commits
       })
```

`ConsumerDeduper` inserts a row into `consumer_inbox(consumer_id, message_id)` with a UNIQUE
constraint on `(consumer_id, message_id)`. Duplicate deliveries hit the constraint and are silently
skipped. `@Transactional` on the consuming method ensures the inbox row and domain writes are
atomic.

---

## Step-by-Step

### 1. Define the Kafka topic constant

Add the topic name to `libs/contracts/...KafkaTopics.java` (shared constants file) if it's new.
Do **not** hard-code topic strings in `@KafkaListener` annotations.

### 2. Create a consumer configuration bean

In the service's `@Configuration` class:

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> myTopicContainerFactory(
        ConsumerFactory<String, String> consumerFactory) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(consumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    return factory;
}
```

Or reuse the existing `defaultKafkaListenerContainerFactory` if the service already has one.

### 3. Write the consumer class

```java
@Service
public class MyTopicConsumer {

    private static final Logger log = LoggerFactory.getLogger(MyTopicConsumer.class);
    private static final String CONSUMER_ID = "my-service.my-topic.v1";

    private final ConsumerDeduper consumerDeduper;
    private final MyDomainService domainService;

    public MyTopicConsumer(ConsumerDeduper consumerDeduper, MyDomainService domainService) {
        this.consumerDeduper = consumerDeduper;
        this.domainService = domainService;
    }

    @KafkaListener(
        topics = "${kafka.topics.my-topic}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "defaultKafkaListenerContainerFactory"
    )
    @Transactional   // ← mandatory: DB write + consumer_inbox row must be atomic
    public void consume(ConsumerRecord<String, String> record) {
        String messageId = record.key();   // use the Kafka message key as the dedup ID
        MDC.put("message_id", messageId);
        try {
            consumerDeduper.process(CONSUMER_ID, messageId, () -> {
                MyEvent event = JsonUtil.deserialize(record.value(), MyEvent.class);
                MDC.put("event_id", event.getEventId());
                log.info("Processing my-topic event");
                domainService.handleMyEvent(event);
            });
        } catch (DuplicateKeyException e) {
            log.debug("Duplicate message skipped: {}", messageId);
        } finally {
            MDC.clear();
        }
    }
}
```

### 4. Register the CONSUMER_ID in documentation

Add a row to the "Consumer Registry" table in `docs/DATA_FLOW.md` (if it exists) or in
`docs/SYSTEM_FLOW.md` so the consumer ID is traceable.

### 5. Configuration in `application.yml`

```yaml
kafka:
  topics:
    my-topic: my.topic.name.v1
spring:
  kafka:
    consumer:
      group-id: my-service-consumer-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

---

## Post-Commit Kafka Publishes (Outbox Pattern)

**Never publish to Kafka directly inside a `@Transactional` method.** Use the transactional
outbox via `KafkaFirstPublisher`:

```java
// Inside your @Transactional service method, AFTER the domain write:
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        kafkaFirstPublisher.publish(topic, key, payload);
    }
});
```

Or use the `OutboxEventWriter` to write an outbox row in the same TX; the outbox poller will
pick it up and publish.

---

## Testing the Consumer

Add a test in the service's unit-test module (`src/test/java/...`):

```java
@Test
void consume_deduplicates_on_second_delivery() {
    // Given: first call processes successfully
    consumer.consume(buildRecord("key-1", """{"eventId":"evt-1"}"""));

    // When: same message delivered again
    consumer.consume(buildRecord("key-1", """{"eventId":"evt-1"}"""));

    // Then: domain service called exactly once
    verify(domainService, times(1)).handleMyEvent(any());
}
```

For E2E coverage, add a test in `tests/e2e/` extending or creating a `<Feature>Test.java`.

### Run tests after implementation

```bash
# Unit tests for the specific service module
python3 scripts/test.py unit --module services/<your-service>

# All unit tests (validates nothing else broke)
python3 scripts/test.py unit

# Coverage gate — must stay ≥ 50% on core modules
python3 scripts/test.py coverage

# E2E tests — catches integration-level regressions
python3 scripts/test.py e2e

# Full live pipeline test (requires stack up)
python3 scripts/test.py smoke

# Run all Maven suites at once
python3 scripts/test.py all
```

---

## Checklist

- [ ] Topic name uses a constant from `KafkaTopics.java`
- [ ] `CONSUMER_ID` is unique across all services
- [ ] Listener method is `@Transactional`
- [ ] `MDC.clear()` in `finally`
- [ ] Error handling: `DuplicateKeyException` (dedup) vs unexpected errors (let propagate → Kafka retry)
- [ ] `application.yml` / `application-local.yml` updated with topic name
- [ ] Unit test covers dedup: `python3 scripts/test.py unit --module services/<your-service>` passes
- [ ] `python3 scripts/test.py e2e` — all 5 classes green
- [ ] `python3 scripts/test.py coverage` — module stays above 50% threshold
- [ ] `consumer_inbox` schema already exists — provided by V1 migration
