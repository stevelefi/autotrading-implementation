package com.autotrading.services.risk;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.autotrading.libs.kafka.DirectKafkaPublisher;
import com.autotrading.services.risk.db.PolicyDecisionLogRepository;
import com.autotrading.services.risk.db.RiskDecisionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.autotrading.services.risk.core.SimplePolicyEngine;
import com.autotrading.services.risk.grpc.RiskDecisionGrpcService;
import com.autotrading.services.risk.runtime.RiskGrpcServerLifecycle;

import io.grpc.ServerInterceptor;

class RiskGrpcServerLifecycleTest {

    private static final ServerInterceptor NOOP = new ServerInterceptor() {
        @Override
        public <ReqT, RespT> io.grpc.ServerCall.Listener<ReqT> interceptCall(
                io.grpc.ServerCall<ReqT, RespT> call,
                io.grpc.Metadata headers,
                io.grpc.ServerCallHandler<ReqT, RespT> next) {
            return next.startCall(call, headers);
        }
    };

    private RiskGrpcServerLifecycle buildLifecycle() {
        RiskDecisionGrpcService service = new RiskDecisionGrpcService(null, new SimplePolicyEngine(),
                mock(RiskDecisionRepository.class), mock(PolicyDecisionLogRepository.class),
                mock(DirectKafkaPublisher.class), new ObjectMapper());
        return new RiskGrpcServerLifecycle(service, NOOP, 0);
    }

    @Test
    void isRunningFalseBeforeStart() {
        assertThat(buildLifecycle().isRunning()).isFalse();
    }

    @Test
    void isAutoStartupTrue() {
        assertThat(buildLifecycle().isAutoStartup()).isTrue();
    }

    @Test
    void getPhaseIsZero() {
        assertThat(buildLifecycle().getPhase()).isZero();
    }

    @Test
    void stopWhenNotStartedIsIdempotent() {
        RiskGrpcServerLifecycle lifecycle = buildLifecycle();
        lifecycle.stop();               // server is null — must not throw
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void stopWithCallbackInvokesCallbackWhenNotRunning() {
        RiskGrpcServerLifecycle lifecycle = buildLifecycle();
        AtomicBoolean called = new AtomicBoolean(false);
        lifecycle.stop(() -> called.set(true));
        assertThat(called.get()).isTrue();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void startBindsPortAndSetsRunning() {
        RiskDecisionGrpcService service = new RiskDecisionGrpcService(null, new SimplePolicyEngine(),
                mock(RiskDecisionRepository.class), mock(PolicyDecisionLogRepository.class),
                mock(DirectKafkaPublisher.class), new ObjectMapper());
        // port 0 → OS picks a free ephemeral port
        RiskGrpcServerLifecycle lifecycle = new RiskGrpcServerLifecycle(service, NOOP, 0);
        try {
            lifecycle.start();
            assertThat(lifecycle.isRunning()).isTrue();
        } finally {
            lifecycle.stop();
        }
    }

    @Test
    void startIsIdempotentWhenAlreadyRunning() {
        RiskDecisionGrpcService service = new RiskDecisionGrpcService(null, new SimplePolicyEngine(),
                mock(RiskDecisionRepository.class), mock(PolicyDecisionLogRepository.class),
                mock(DirectKafkaPublisher.class), new ObjectMapper());
        RiskGrpcServerLifecycle lifecycle = new RiskGrpcServerLifecycle(service, NOOP, 0);
        try {
            lifecycle.start();
            lifecycle.start(); // second call should be a no-op
            assertThat(lifecycle.isRunning()).isTrue();
        } finally {
            lifecycle.stop();
        }
    }
}
