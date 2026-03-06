package com.autotrading.services.ibkr.grpc;

import com.autotrading.command.v1.BrokerCommandServiceGrpc;
import com.autotrading.command.v1.CancelOrderRequest;
import com.autotrading.command.v1.ReplaceOrderRequest;
import com.autotrading.command.v1.SubmitOrderRequest;
import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class BrokerCommandGrpcService extends BrokerCommandServiceGrpc.BrokerCommandServiceImplBase {
  private static final Logger log = LoggerFactory.getLogger(BrokerCommandGrpcService.class);
  private final BrokerConnectorEngine engine;

  public BrokerCommandGrpcService(BrokerConnectorEngine engine) {
    this.engine = engine;
  }

  @Override
  public void submitOrder(SubmitOrderRequest request, StreamObserver<com.autotrading.command.v1.SubmitOrderResponse> responseObserver) {
    try {
      MDC.put("agent_id", request.getAgentId());
      MDC.put("order_intent_id", request.getOrderIntentId());
      MDC.put("instrument_id", request.getInstrumentId());
      responseObserver.onNext(engine.submit(request));
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("ibkr submitOrder failed: {}", ex.getMessage(), ex);
      responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    } finally {
      MDC.clear();
    }
  }

  @Override
  public void cancelOrder(CancelOrderRequest request, StreamObserver<com.autotrading.command.v1.CancelOrderResponse> responseObserver) {
    try {
      MDC.put("agent_id", request.getAgentId());
      MDC.put("order_intent_id", request.getOrderIntentId());
      responseObserver.onNext(engine.cancel(request));
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("ibkr cancelOrder failed: {}", ex.getMessage(), ex);
      responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    } finally {
      MDC.clear();
    }
  }

  @Override
  public void replaceOrder(ReplaceOrderRequest request, StreamObserver<com.autotrading.command.v1.ReplaceOrderResponse> responseObserver) {
    try {
      MDC.put("agent_id", request.getAgentId());
      MDC.put("order_intent_id", request.getOrderIntentId());
      responseObserver.onNext(engine.replace(request));
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("ibkr replaceOrder failed: {}", ex.getMessage(), ex);
      responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    } finally {
      MDC.clear();
    }
  }
}
