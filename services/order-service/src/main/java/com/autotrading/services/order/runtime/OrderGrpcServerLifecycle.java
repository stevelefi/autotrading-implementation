package com.autotrading.services.order.runtime;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import com.autotrading.services.order.grpc.OrderCommandGrpcService;

import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

public class OrderGrpcServerLifecycle implements SmartLifecycle {
  private static final Logger log = LoggerFactory.getLogger(OrderGrpcServerLifecycle.class);

  private final OrderCommandGrpcService service;
  private final ServerInterceptor correlationInterceptor;
  private final int port;
  private volatile boolean running;
  private volatile Server server;
  // Pre-sized executor: core threads are created eagerly so gRPC calls never pay
  // the ~15-20ms cost of new thread creation on the hot path.
  private final ExecutorService grpcExecutor = new ThreadPoolExecutor(
      4, 32, 60L, TimeUnit.SECONDS,
      new SynchronousQueue<>(),
      r -> { Thread t = new Thread(r, "order-grpc"); t.setDaemon(true); return t; });

  public OrderGrpcServerLifecycle(OrderCommandGrpcService service, ServerInterceptor correlationInterceptor, int port) {
    this.service = service;
    this.correlationInterceptor = correlationInterceptor;
    this.port = port;
  }

  @Override
  public void start() {
    if (running) {
      return;
    }
    try {
      server = NettyServerBuilder.forPort(port)
          .addService(ServerInterceptors.intercept(service, correlationInterceptor))
          .executor(grpcExecutor)
          .build()
          .start();
      running = true;
      log.info("order gRPC server started on {}", port);
    } catch (IOException ex) {
      throw new IllegalStateException("failed to start order gRPC server", ex);
    }
  }

  @Override
  public void stop() {
    if (server != null) {
      server.shutdown();
      try {
        if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
          server.shutdownNow();
          log.warn("order gRPC server forced shutdown after 5s timeout");
        }
      } catch (InterruptedException e) {
        server.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
    running = false;
  }

  @Override
  public void stop(Runnable callback) {
    stop();
    callback.run();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public int getPhase() {
    return 0;
  }
}
