package com.autotrading.libs.reliability.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboxPollerLifecycleTest {

  private OutboxPollerLifecycle lifecycle;

  /**
   * Concrete stub — bypasses Mockito/ByteBuddy limitations on Java 25.
   * Passes null deps to OutboxDispatcher (constructor only assigns fields).
   */
  private static OutboxDispatcher stub(AtomicInteger callCounter, int returnValue) {
    return new OutboxDispatcher(null, null, null) {
      @Override
      public int dispatchBatch(int batchSize) {
        callCounter.incrementAndGet();
        return returnValue;
      }
    };
  }

  @AfterEach
  void tearDown() {
    if (lifecycle != null && lifecycle.isRunning()) {
      lifecycle.stop();
    }
  }

  @Test
  void isNotRunningBeforeStart() {
    lifecycle = new OutboxPollerLifecycle(stub(new AtomicInteger(), 0));
    assertThat(lifecycle.isRunning()).isFalse();
  }

  @Test
  void phaseIsHighToStartAfterKafkaListeners() {
    lifecycle = new OutboxPollerLifecycle(stub(new AtomicInteger(), 0));
    assertThat(lifecycle.getPhase()).isGreaterThan(0);
  }

  @Test
  void startSetsRunningTrue() {
    lifecycle = new OutboxPollerLifecycle(stub(new AtomicInteger(), 0));
    lifecycle.start();
    assertThat(lifecycle.isRunning()).isTrue();
  }

  @Test
  void stopSetsRunningFalse() {
    lifecycle = new OutboxPollerLifecycle(stub(new AtomicInteger(), 0));
    lifecycle.start();
    lifecycle.stop();
    assertThat(lifecycle.isRunning()).isFalse();
  }

  @Test
  void doubleStartIsIdempotent() {
    lifecycle = new OutboxPollerLifecycle(stub(new AtomicInteger(), 0));
    lifecycle.start();
    lifecycle.start(); // second call is a no-op
    assertThat(lifecycle.isRunning()).isTrue();
  }

  @Test
  void stopWhenNotStartedIsIdempotent() {
    lifecycle = new OutboxPollerLifecycle(stub(new AtomicInteger(), 0));
    assertThatCode(lifecycle::stop).doesNotThrowAnyException();
    assertThat(lifecycle.isRunning()).isFalse();
  }

  @Test
  void pollerCallsDispatchBatch() throws Exception {
    var counter = new AtomicInteger(0);
    lifecycle = new OutboxPollerLifecycle(stub(counter, 1));
    lifecycle.start();
    // poll interval is 500ms — wait up to 2s for at least one call
    long deadline = System.currentTimeMillis() + 2000;
    while (counter.get() == 0 && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertThat(counter.get()).isGreaterThanOrEqualTo(1);
  }
}

