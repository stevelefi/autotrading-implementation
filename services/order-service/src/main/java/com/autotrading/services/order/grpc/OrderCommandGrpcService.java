package com.autotrading.services.order.grpc;

import com.autotrading.command.v1.BrokerCommandServiceGrpc;
import com.autotrading.command.v1.CreateOrderIntentRequest;
import com.autotrading.command.v1.OrderCommandServiceGrpc;
import com.autotrading.services.order.core.OrderSafetyEngine;
import io.grpc.stub.StreamObserver;

public class OrderCommandGrpcService extends OrderCommandServiceGrpc.OrderCommandServiceImplBase {
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
    responseObserver.onNext(engine.createOrderIntent(request, brokerStub));
    responseObserver.onCompleted();
  }

  public OrderSafetyEngine engine() {
    return engine;
  }
}
