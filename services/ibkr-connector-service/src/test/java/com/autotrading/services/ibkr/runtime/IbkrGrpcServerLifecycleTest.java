package com.autotrading.services.ibkr.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for IbkrGrpcServerLifecycle covering state-machine methods
 * (isRunning, isAutoStartup, getPhase, stop before start).
 * Service and interceptor are null because start() is never called here —
 * those refs are only accessed in start(). The start() path binding a real
 * gRPC port is covered by the integration / smoke-test suite.
 */
class IbkrGrpcServerLifecycleTest {

  private IbkrGrpcServerLifecycle lifecycle() {
    // nulls are safe: constructor only assigns fields, used only in start()
    return new IbkrGrpcServerLifecycle(null, null, 0);
  }

  @Test
  void isNotRunningBeforeStart() {
    assertThat(lifecycle().isRunning()).isFalse();
  }

  @Test
  void isAutoStartupIsTrue() {
    assertThat(lifecycle().isAutoStartup()).isTrue();
  }

  @Test
  void phaseIsZero() {
    assertThat(lifecycle().getPhase()).isEqualTo(0);
  }

  @Test
  void stopBeforeStartDoesNotThrow() {
    var lc = lifecycle();
    assertThatCode(lc::stop).doesNotThrowAnyException();
    assertThat(lc.isRunning()).isFalse();
  }

  @Test
  void stopWithCallbackBeforeStartInvokesCallback() {
    var lc = lifecycle();
    var called = new boolean[]{false};
    assertThatCode(() -> lc.stop(() -> called[0] = true)).doesNotThrowAnyException();
    assertThat(called[0]).isTrue();
  }
}

