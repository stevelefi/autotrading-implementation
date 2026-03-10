package com.autotrading.libs.observability;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet filter that extracts correlation identifiers from inbound HTTP
 * request headers and propagates them via SLF4J MDC for the duration of
 * the request.
 *
 * <p>Headers inspected (case-insensitive per HTTP spec):
 * <ul>
 *   <li>{@code X-Trace-Id}        → MDC key {@code trace_id}</li>
 *   <li>{@code X-Request-Id}      → MDC key {@code request_id}</li>
 *   <li>{@code X-Client-Event-Id} → MDC key {@code client_event_id}</li>
 *   <li>{@code X-Principal-Id}    → MDC key {@code principal_id}</li>
 * </ul>
 *
 * <p>If a header is absent or blank a random UUID is generated so that
 * every request is always traceable.  All MDC entries are cleaned up in
 * the {@code finally} block to prevent leakage between requests on
 * pooled container threads.
 */
public class HttpCorrelationFilter extends OncePerRequestFilter {

  static final String MDC_TRACE_ID        = "trace_id";
  static final String MDC_REQUEST_ID      = "request_id";
  static final String MDC_CLIENT_EVENT_ID = "client_event_id";
  static final String MDC_PRINCIPAL_ID    = "principal_id";

  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain)
      throws ServletException, IOException {

    String traceId       = resolve(request, "X-Trace-Id");
    String requestId     = resolve(request, "X-Request-Id");
    String clientEventId = resolve(request, "X-Client-Event-Id");
    String principalId   = resolve(request, "X-Principal-Id");

    MDC.put(MDC_TRACE_ID,        traceId);
    MDC.put(MDC_REQUEST_ID,      requestId);
    MDC.put(MDC_CLIENT_EVENT_ID, clientEventId);
    MDC.put(MDC_PRINCIPAL_ID,    principalId);

    // Echo the resolved trace-id back so callers can correlate responses.
    response.setHeader("X-Trace-Id", traceId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_TRACE_ID);
      MDC.remove(MDC_REQUEST_ID);
      MDC.remove(MDC_CLIENT_EVENT_ID);
      MDC.remove(MDC_PRINCIPAL_ID);
    }
  }

  private static String resolve(HttpServletRequest request, String headerName) {
    String val = request.getHeader(headerName);
    return (val != null && !val.isBlank()) ? val : UUID.randomUUID().toString();
  }
}
