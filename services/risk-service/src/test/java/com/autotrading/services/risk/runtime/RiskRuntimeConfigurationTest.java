package com.autotrading.services.risk.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.autotrading.libs.reliability.outbox.OutboxRepository;
import com.autotrading.services.risk.db.PolicyDecisionLogRepository;
import com.autotrading.services.risk.db.RiskDecisionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.autotrading.libs.observability.GrpcCorrelationServerInterceptor;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.services.risk.grpc.RiskDecisionGrpcService;

import io.grpc.ManagedChannel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RiskRuntimeConfigurationTest {

    private final RiskRuntimeConfiguration config = new RiskRuntimeConfiguration();

    @Test
    void reliabilityMetricsBeanIsCreated() {
        ReliabilityMetrics metrics = config.reliabilityMetrics(new SimpleMeterRegistry());
        assertThat(metrics).isNotNull();
    }

    @Test
    void simplePolicyEngineBeanIsCreated() {
        assertThat(config.simplePolicyEngine()).isNotNull();
    }

    @Test
    void orderGrpcChannelBeanIsCreated() {
        ManagedChannel channel = config.orderGrpcChannel("localhost", 1);
        try {
            assertThat(channel).isNotNull();
        } finally {
            channel.shutdownNow();
        }
    }

    @Test
    void orderCommandStubBeanIsCreated() {
        ManagedChannel channel = config.orderGrpcChannel("localhost", 1);
        try {
            assertThat(config.orderCommandStub(channel)).isNotNull();
        } finally {
            channel.shutdownNow();
        }
    }

    @Test
    void riskDecisionGrpcServiceBeanIsCreated() {
        RiskDecisionGrpcService service = config.riskDecisionGrpcService(null, config.simplePolicyEngine(),
                mock(RiskDecisionRepository.class), mock(PolicyDecisionLogRepository.class),
                mock(OutboxRepository.class), new ObjectMapper());
        assertThat(service).isNotNull();
    }

    @Test
    void riskGrpcServerLifecycleBeanIsCreated() {
        RiskDecisionGrpcService service = config.riskDecisionGrpcService(null, config.simplePolicyEngine(),
                mock(RiskDecisionRepository.class), mock(PolicyDecisionLogRepository.class),
                mock(OutboxRepository.class), new ObjectMapper());
        GrpcCorrelationServerInterceptor interceptor = new GrpcCorrelationServerInterceptor();
        RiskGrpcServerLifecycle lifecycle = config.riskGrpcServerLifecycle(service, interceptor, 0);
        assertThat(lifecycle).isNotNull();
        assertThat(lifecycle.isRunning()).isFalse();
    }
}
