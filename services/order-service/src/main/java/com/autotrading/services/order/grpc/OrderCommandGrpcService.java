package com.autotrading.services.order.grpc;

import com.autotrading.command.v1.BrokerCommandServiceGrpc;
import com.autotrading.command.v1.CreateOrderIntentRequest;
import com.autotrading.command.v1.OrderCommandServiceGrpc;
import com.autotrading.services.order.core.OrderSafetyEngine;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class OrderCommandGrpcService extends OrderCommandServiceGrpc.OrderCommandServiceImplBase {
  private static final Logger log = LoggerFactory.getLogger(OrderCommandGrpcService.class);
  private final OrderSafetyEngine engine;
  private final BrokerCommandServiceGrpc.BrokerCommandServiceBlockingStub brokerStub;

  public OrderCommandGrpcService(OrderSafetyEngine engine,
                                 BrokerCommandServiceGrpc.BrokerCommandServiceBlockingStub brokerStub) {
    this.engine = engine;
    this.brokerStub = brokerStub;
  }

  @Override
  public void createOrderIntent(CreateOrderIntentRequest request,
                                StreamObserver<com.autotrading.command.v1.CreateOrderIntentResponse> responseObserver) {
    try {
      // Set domain MDC keys for structured logging
      MDC.put("agent_id", request.getAgentId());
      MDC.put("signal_id", request.getSignalId());
      MDC.put("instrument_id", request.getInstrumentId());

      responseObserver.onNext(engine.createOrderIntent(request, brokerStub));
      responseObserver.onCompleted();
    } catch (IllegalArgumentException ex) {
      responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
    } catch (Exception ex) {
      log.error("order createOrderIntent failed: {}", ex.getMessage(), ex);
      responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    } finally {
      MDC.clear();
    }
  }

  public OrderSafetyEngine engine() {
    return engine;
  }
}
