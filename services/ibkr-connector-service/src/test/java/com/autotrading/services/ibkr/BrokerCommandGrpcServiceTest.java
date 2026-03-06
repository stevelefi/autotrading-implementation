package com.autotrading.services.ibkr;

import com.autotrading.command.v1.CancelOrderRequest;
import com.autotrading.command.v1.CancelOrderResponse;
import com.autotrading.command.v1.CommandStatus;
import com.autotrading.command.v1.ReplaceOrderRequest;
import com.autotrading.command.v1.ReplaceOrderResponse;
import com.autotrading.command.v1.RequestContext;
import com.autotrading.command.v1.SubmitOrderRequest;
import com.autotrading.command.v1.SubmitOrderResponse;
import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import com.autotrading.services.ibkr.grpc.BrokerCommandGrpcService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.autotrading.libs.idempotency.InMemoryIdempotencyService;
import com.autotrading.libs.reliability.outbox.OutboxRepository;
import com.autotrading.services.ibkr.db.BrokerOrderRepository;
import com.autotrading.services.ibkr.db.ExecutionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class BrokerCommandGrpcServiceTest {

    private final BrokerConnectorEngine engine = new BrokerConnectorEngine(
            new InMemoryIdempotencyService(), mock(BrokerOrderRepository.class),
            mock(ExecutionRepository.class), mock(OutboxRepository.class), new ObjectMapper());
    private final BrokerCommandGrpcService service = new BrokerCommandGrpcService(engine);

    private static RequestContext ctx(String idem) {
        return RequestContext.newBuilder()
                .setTraceId("trc-1")
                .setRequestId("req-1")
                .setIdempotencyKey(idem)
                .setPrincipalId("svc-order")
                .build();
    }

    // -------------------------------------------------------------------
    // submitOrder
    // -------------------------------------------------------------------

    @Test
    void submitOrderDelegatesToEngineAndCallsOnNext() {
        SubmitOrderRequest request = SubmitOrderRequest.newBuilder()
                .setRequestContext(ctx("idem-submit-grpc"))
                .setOrderIntentId("ord-grpc-1")
                .setSide("BUY")
                .setQty(5)
                .setOrderType("MKT")
                .setTimeInForce("DAY")
                .setSubmissionDeadlineMs(60000)
                .build();

        List<SubmitOrderResponse> responses = new ArrayList<>();
        List<Boolean> completed = new ArrayList<>();

        service.submitOrder(request, new StreamObserver<SubmitOrderResponse>() {
            @Override public void onNext(SubmitOrderResponse v) { responses.add(v); }
            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() { completed.add(true); }
        });

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
        assertThat(completed).hasSize(1);
    }

    // -------------------------------------------------------------------
    // cancelOrder
    // -------------------------------------------------------------------

    @Test
    void cancelOrderDelegatesToEngineAndCallsOnNext() {
        CancelOrderRequest request = CancelOrderRequest.newBuilder()
                .setRequestContext(ctx("idem-cancel-grpc"))
                .setAgentId("agent-1")
                .setOrderIntentId("ord-grpc-2")
                .setReason("user cancel")
                .build();

        List<CancelOrderResponse> responses = new ArrayList<>();
        List<Boolean> completed = new ArrayList<>();

        service.cancelOrder(request, new StreamObserver<CancelOrderResponse>() {
            @Override public void onNext(CancelOrderResponse v) { responses.add(v); }
            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() { completed.add(true); }
        });

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
        assertThat(completed).hasSize(1);
    }

    // -------------------------------------------------------------------
    // replaceOrder
    // -------------------------------------------------------------------

    @Test
    void replaceOrderDelegatesToEngineAndCallsOnNext() {
        ReplaceOrderRequest request = ReplaceOrderRequest.newBuilder()
                .setRequestContext(ctx("idem-replace-grpc"))
                .setAgentId("agent-1")
                .setOrderIntentId("ord-grpc-3")
                .setNewQty(20)
                .setNewLimitPrice("42.00")
                .setReason("size increase")
                .build();

        List<ReplaceOrderResponse> responses = new ArrayList<>();
        List<Boolean> completed = new ArrayList<>();

        service.replaceOrder(request, new StreamObserver<ReplaceOrderResponse>() {
            @Override public void onNext(ReplaceOrderResponse v) { responses.add(v); }
            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() { completed.add(true); }
        });

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
        assertThat(completed).hasSize(1);
    }

    // -------------------------------------------------------------------
    // totalSubmitCount via engine
    // -------------------------------------------------------------------

    @Test
    void totalSubmitCountReflectsAllGrpcSubmits() {
        SubmitOrderRequest r1 = SubmitOrderRequest.newBuilder()
                .setRequestContext(ctx("idem-total-1"))
                .setOrderIntentId("ord-total-1")
                .setSide("BUY").setQty(1).setOrderType("MKT").setTimeInForce("DAY")
                .setSubmissionDeadlineMs(60000).build();
        SubmitOrderRequest r2 = SubmitOrderRequest.newBuilder()
                .setRequestContext(ctx("idem-total-2"))
                .setOrderIntentId("ord-total-2")
                .setSide("SELL").setQty(2).setOrderType("MKT").setTimeInForce("DAY")
                .setSubmissionDeadlineMs(60000).build();

        service.submitOrder(r1, new StreamObserver<SubmitOrderResponse>() {
            @Override public void onNext(SubmitOrderResponse v) {}
            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() {}
        });
        service.submitOrder(r2, new StreamObserver<SubmitOrderResponse>() {
            @Override public void onNext(SubmitOrderResponse v) {}
            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() {}
        });

        assertThat(engine.totalSubmitCount()).isEqualTo(2);
    }
}
