package com.autotrading.libs.observability;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * gRPC server interceptor that extracts correlation identifiers
 * (x-trace-id, x-request-id) from incoming metadata headers and
 * propagates them via SLF4J MDC so that all log lines emitted
 * during the call carry the correlation context.
 *
 * <p>If a header is absent or blank a random UUID is generated so
 * that every call is always traceable.  The MDC entries are
 * cleaned up after each listener callback completes.
 */
public class GrpcCorrelationServerInterceptor implements ServerInterceptor {

  private static final Logger log =
      LoggerFactory.getLogger(GrpcCorrelationServerInterceptor.class);

  static final Metadata.Key<String> TRACE_ID_KEY =
      Metadata.Key.of("x-trace-id", Metadata.ASCII_STRING_MARSHALLER);
  static final Metadata.Key<String> REQUEST_ID_KEY =
      Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

  static final String MDC_TRACE_ID   = "traceId";
  static final String MDC_REQUEST_ID = "requestId";

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call,
      Metadata headers,
      ServerCallHandler<ReqT, RespT> next) {

    String traceId   = resolveHeader(headers, TRACE_ID_KEY);
    String requestId = resolveHeader(headers, REQUEST_ID_KEY);

    log.debug("gRPC call {} traceId={} requestId={}",
        call.getMethodDescriptor().getFullMethodName(), traceId, requestId);

    return new CorrelatedListener<>(next.startCall(call, headers), traceId, requestId);
  }

  // -----------------------------------------------------------------------

  private static String resolveHeader(Metadata headers, Metadata.Key<String> key) {
    String val = headers.get(key);
    return (val != null && !val.isBlank()) ? val : UUID.randomUUID().toString();
  }

  // -----------------------------------------------------------------------

  private static final class CorrelatedListener<ReqT> extends ServerCall.Listener<ReqT> {

    private final ServerCall.Listener<ReqT> delegate;
    private final String traceId;
    private final String requestId;

    CorrelatedListener(ServerCall.Listener<ReqT> delegate,
                       String traceId, String requestId) {
      this.delegate  = delegate;
      this.traceId   = traceId;
      this.requestId = requestId;
    }

    @Override public void onMessage(ReqT message)  { withMdc(() -> delegate.onMessage(message)); }
    @Override public void onHalfClose()             { withMdc(delegate::onHalfClose); }
    @Override public void onCancel()                { withMdc(delegate::onCancel); }
    @Override public void onComplete()              { withMdc(delegate::onComplete); }
    @Override public void onReady()                 { withMdc(delegate::onReady); }

    private void withMdc(Runnable action) {
      MDC.put(MDC_TRACE_ID,   traceId);
      MDC.put(MDC_REQUEST_ID, requestId);
      try {
        action.run();
      } finally {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_REQUEST_ID);
      }
    }
  }
}
