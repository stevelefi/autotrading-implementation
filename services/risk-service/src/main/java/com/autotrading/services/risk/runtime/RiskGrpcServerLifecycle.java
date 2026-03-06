package com.autotrading.services.risk.runtime;

import com.autotrading.services.risk.grpc.RiskDecisionGrpcService;
import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

public class RiskGrpcServerLifecycle implements SmartLifecycle {
  private static final Logger log = LoggerFactory.getLogger(RiskGrpcServerLifecycle.class);

  private final RiskDecisionGrpcService service;
  private final ServerInterceptor correlationInterceptor;
  private final int port;
  private volatile boolean running;
  private volatile Server server;

  public RiskGrpcServerLifecycle(RiskDecisionGrpcService service, ServerInterceptor correlationInterceptor, int port) {
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
          .build()
          .start();
      running = true;
      log.info("risk gRPC server started on {}", port);
    } catch (IOException ex) {
      throw new IllegalStateException("failed to start risk gRPC server", ex);
    }
  }

  @Override
  public void stop() {
    if (server != null) {
      server.shutdown();
      try {
        if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
          server.shutdownNow();
          log.warn("risk gRPC server forced shutdown after 5s timeout");
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
