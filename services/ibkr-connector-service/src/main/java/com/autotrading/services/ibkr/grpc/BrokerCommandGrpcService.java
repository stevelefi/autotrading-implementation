package com.autotrading.services.ibkr.grpc;

import com.autotrading.command.v1.BrokerCommandServiceGrpc;
import com.autotrading.command.v1.CancelOrderRequest;
import com.autotrading.command.v1.ReplaceOrderRequest;
import com.autotrading.command.v1.SubmitOrderRequest;
import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import io.grpc.stub.StreamObserver;

public class BrokerCommandGrpcService extends BrokerCommandServiceGrpc.BrokerCommandServiceImplBase {
  private final BrokerConnectorEngine engine;

  public BrokerCommandGrpcService(BrokerConnectorEngine engine) {
    this.engine = engine;
  }

  @Override
  public void submitOrder(SubmitOrderRequest request, StreamObserver<com.autotrading.command.v1.SubmitOrderResponse> responseObserver) {
    responseObserver.onNext(engine.submit(request));
    responseObserver.onCompleted();
  }

  @Override
  public void cancelOrder(CancelOrderRequest request, StreamObserver<com.autotrading.command.v1.CancelOrderResponse> responseObserver) {
    responseObserver.onNext(engine.cancel(request));
    responseObserver.onCompleted();
  }

  @Override
  public void replaceOrder(ReplaceOrderRequest request, StreamObserver<com.autotrading.command.v1.ReplaceOrderResponse> responseObserver) {
    responseObserver.onNext(engine.replace(request));
    responseObserver.onCompleted();
  }
}
