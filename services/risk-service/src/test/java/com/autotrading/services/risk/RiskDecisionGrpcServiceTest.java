package com.autotrading.services.risk;

import com.autotrading.command.v1.CreateOrderIntentRequest;
import com.autotrading.command.v1.CreateOrderIntentResponse;
import com.autotrading.command.v1.EvaluateSignalRequest;
import com.autotrading.command.v1.EvaluateSignalResponse;
import com.autotrading.command.v1.OrderCommandServiceGrpc;
import com.autotrading.command.v1.RequestContext;
import com.autotrading.services.risk.core.SimplePolicyEngine;
import com.autotrading.services.risk.grpc.RiskDecisionGrpcService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.autotrading.libs.reliability.outbox.OutboxRepository;
import com.autotrading.services.risk.db.RiskDecisionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class RiskDecisionGrpcServiceTest {

    private Server         orderServer;
    private ManagedChannel orderChannel;
    private OrderCommandServiceGrpc.OrderCommandServiceBlockingStub orderStub;

    private final SimplePolicyEngine policyEngine = new SimplePolicyEngine();

    @BeforeEach
    void setUp() throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        orderServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new OrderCommandServiceGrpc.OrderCommandServiceImplBase() {
                    @Override
                    public void createOrderIntent(CreateOrderIntentRequest request,
                                                  StreamObserver<CreateOrderIntentResponse> r) {
                        r.onNext(CreateOrderIntentResponse.newBuilder().build());
                        r.onCompleted();
                    }
                })
                .build()
                .start();
        orderChannel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        orderStub    = OrderCommandServiceGrpc.newBlockingStub(orderChannel);
    }

    @AfterEach
    void tearDown() {
        if (orderChannel != null) orderChannel.shutdownNow();
        if (orderServer  != null) orderServer.shutdownNow();
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private static <T> StreamObserver<T> collector(List<T> out, List<Throwable> errors) {
        return new StreamObserver<T>() {
            @Override public void onNext(T value)      { out.add(value); }
            @Override public void onError(Throwable t) { errors.add(t); }
            @Override public void onCompleted()        {}
        };
    }

    private static EvaluateSignalRequest fullRequest(String idempotencyKey) {
        return EvaluateSignalRequest.newBuilder()
                .setRequestContext(RequestContext.newBuilder()
                        .setTraceId("trc-1")
                        .setRequestId("req-1")
                        .setIdempotencyKey(idempotencyKey)
                        .setPrincipalId("svc-agent")
                        .build())
                .setAgentId("agent-1")
                .setInstrumentId("eq_tqqq")
                .setSignalId("sig-1")
                .setReason("strategy-signal")
                .setTradeEventId("trade-1")
                .setRawEventId("raw-1")
                .setSourceSystem("agent-runtime-service")
                .setOriginSourceType("TRADER_UI")
                .setOriginSourceEventId("ui-1")
                .setSide("BUY")
                .setQty(1)
                .setOrderType("MKT")
                .setTimeInForce("DAY")
                .build();
    }

    // -------------------------------------------------------------------
    // validateLineage failure paths (stub not reached — can pass null)
    // -------------------------------------------------------------------

    @Test
    void missingLineageFieldsResultInInvalidArgumentError() {
        RiskDecisionGrpcService service = new RiskDecisionGrpcService(null, policyEngine,
                mock(RiskDecisionRepository.class), mock(OutboxRepository.class), new ObjectMapper());
        List<Throwable> errors = new ArrayList<>();

        service.evaluateSignal(EvaluateSignalRequest.newBuilder().build(),
                collector(new ArrayList<>(), errors));

        assertThat(errors).hasSize(1);
        StatusRuntimeException ex = (StatusRuntimeException) errors.get(0);
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
    }

    @Test
    void externalSystemSourceRequiresOriginEventId() {
        RiskDecisionGrpcService service = new RiskDecisionGrpcService(null, policyEngine,
                mock(RiskDecisionRepository.class), mock(OutboxRepository.class), new ObjectMapper());
        List<Throwable> errors = new ArrayList<>();

        service.evaluateSignal(EvaluateSignalRequest.newBuilder()
                        .setTradeEventId("t1")
                        .setRawEventId("r1")
                        .setSourceSystem("ext-sys")
                        .setOriginSourceType("EXTERNAL_SYSTEM")
                        .build(),
                collector(new ArrayList<>(), errors));

        assertThat(errors).hasSize(1);
        assertThat(((StatusRuntimeException) errors.get(0)).getStatus().getCode())
                .isEqualTo(Status.INVALID_ARGUMENT.getCode());
    }

    // -------------------------------------------------------------------
    // Happy path — real in-process order stub
    // -------------------------------------------------------------------

    @Test
    void evaluateSignalSuccessCallsOrderStubAndRecordsAuditEvent() {
        RiskDecisionGrpcService service = new RiskDecisionGrpcService(orderStub, policyEngine,
                mock(RiskDecisionRepository.class), mock(OutboxRepository.class), new ObjectMapper());
        List<EvaluateSignalResponse> responses = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();

        service.evaluateSignal(fullRequest("idem-1"), collector(responses, errors));

        assertThat(errors).isEmpty();
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getPolicyVersion()).isNotBlank();
        assertThat(service.auditEvents()).hasSize(1);
        assertThat(service.auditEvents().get(0).agentId()).isEqualTo("agent-1");
    }

    @Test
    void multipleSuccessfulCallsAccumulateAuditEvents() {
        RiskDecisionGrpcService service = new RiskDecisionGrpcService(orderStub, policyEngine,
                mock(RiskDecisionRepository.class), mock(OutboxRepository.class), new ObjectMapper());

        service.evaluateSignal(fullRequest("idem-a"), collector(new ArrayList<>(), new ArrayList<>()));
        service.evaluateSignal(fullRequest("idem-b"), collector(new ArrayList<>(), new ArrayList<>()));

        assertThat(service.auditEvents()).hasSize(2);
    }

    // -------------------------------------------------------------------
    // auditEvents
    // -------------------------------------------------------------------

    @Test
    void auditEventsAreEmptyInitially() {
        RiskDecisionGrpcService service = new RiskDecisionGrpcService(null, policyEngine,
                mock(RiskDecisionRepository.class), mock(OutboxRepository.class), new ObjectMapper());
        assertThat(service.auditEvents()).isEmpty();
    }
}
