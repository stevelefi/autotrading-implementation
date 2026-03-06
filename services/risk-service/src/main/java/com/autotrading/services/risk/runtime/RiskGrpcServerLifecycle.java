package com.autotrading.services.risk.runtime;

import com.autotrading.services.risk.grpc.RiskDecisionGrpcService;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

public class RiskGrpcServerLifecycle implements SmartLifecycle {
  private static final Logger log = LoggerFactory.getLogger(RiskGrpcServerLifecycle.class);

  private final RiskDecisionGrpcService service;
  private final int port;
  private volatile boolean running;
  private Server server;

  public RiskGrpcServerLifecycle(RiskDecisionGrpcService service, int port) {
    this.service = service;
    this.port = port;
  }

  @Override
  public void start() {
    if (running) {
      return;
    }
    try {
      server = NettyServerBuilder.forPort(port).addService(service).build().start();
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
