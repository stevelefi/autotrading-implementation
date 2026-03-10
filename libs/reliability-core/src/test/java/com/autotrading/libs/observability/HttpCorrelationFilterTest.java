package com.autotrading.libs.observability;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static com.autotrading.libs.observability.HttpCorrelationFilter.MDC_CLIENT_EVENT_ID;
import static com.autotrading.libs.observability.HttpCorrelationFilter.MDC_PRINCIPAL_ID;
import static com.autotrading.libs.observability.HttpCorrelationFilter.MDC_REQUEST_ID;
import static com.autotrading.libs.observability.HttpCorrelationFilter.MDC_TRACE_ID;

import jakarta.servlet.ServletException;

/**
 * Unit tests for {@link HttpCorrelationFilter} verifying that:
 * <ul>
 *   <li>All four snake_case MDC keys are set from headers when present</li>
 *   <li>Each key falls back to a UUID when the header is absent</li>
 *   <li>All four keys are cleared from MDC after the filter chain completes</li>
 *   <li>The resolved trace-id is echoed back in the response header</li>
 * </ul>
 */
class HttpCorrelationFilterTest {

    private final HttpCorrelationFilter filter = new HttpCorrelationFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // -------------------------------------------------------------------
    // Happy-path: headers present
    // -------------------------------------------------------------------

    @Test
    void allFourMdcKeysAreSetFromHeadersWhenPresent() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id",        "tid-1");
        request.addHeader("X-Request-Id",      "rid-1");
        request.addHeader("X-Client-Event-Id", "idem-1");
        request.addHeader("X-Principal-Id",    "pid-1");

        var capturedMdc = new String[4];

        filter.doFilterInternal(request, new MockHttpServletResponse(), (req, res) -> {
            capturedMdc[0] = MDC.get(MDC_TRACE_ID);
            capturedMdc[1] = MDC.get(MDC_REQUEST_ID);
            capturedMdc[2] = MDC.get(MDC_CLIENT_EVENT_ID);
            capturedMdc[3] = MDC.get(MDC_PRINCIPAL_ID);
        });

        assertThat(capturedMdc[0]).isEqualTo("tid-1");
        assertThat(capturedMdc[1]).isEqualTo("rid-1");
        assertThat(capturedMdc[2]).isEqualTo("idem-1");
        assertThat(capturedMdc[3]).isEqualTo("pid-1");
    }

    @Test
    void mdcKeyNamesUseSnakeCase() throws ServletException, IOException {
        // Guard against regression — constants must use snake_case not camelCase
        assertThat(MDC_TRACE_ID).isEqualTo("trace_id");
        assertThat(MDC_REQUEST_ID).isEqualTo("request_id");
        assertThat(MDC_CLIENT_EVENT_ID).isEqualTo("client_event_id");
        assertThat(MDC_PRINCIPAL_ID).isEqualTo("principal_id");
    }

    // -------------------------------------------------------------------
    // Fallback: headers absent → UUID generated
    // -------------------------------------------------------------------

    @Test
    void missingHeadersFallBackToUuid() throws ServletException, IOException {
        var request  = new MockHttpServletRequest(); // no headers set
        var response = new MockHttpServletResponse();

        var capturedMdc = new String[4];

        filter.doFilterInternal(request, response, (req, res) -> {
            capturedMdc[0] = MDC.get(MDC_TRACE_ID);
            capturedMdc[1] = MDC.get(MDC_REQUEST_ID);
            capturedMdc[2] = MDC.get(MDC_CLIENT_EVENT_ID);
            capturedMdc[3] = MDC.get(MDC_PRINCIPAL_ID);
        });

        for (String value : capturedMdc) {
            assertThat(value).isNotNull().isNotBlank();
            // Must be a valid UUID
            assertThat(UUID.fromString(value)).isNotNull();
        }
    }

    // -------------------------------------------------------------------
    // Cleanup: MDC cleared after filter chain
    // -------------------------------------------------------------------

    @Test
    void allMdcKeysAreClearedAfterFilterChainCompletes() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id",        "tid-cleanup");
        request.addHeader("X-Request-Id",      "rid-cleanup");
        request.addHeader("X-Client-Event-Id", "idem-cleanup");
        request.addHeader("X-Principal-Id",    "pid-cleanup");

        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get(MDC_TRACE_ID)).isNull();
        assertThat(MDC.get(MDC_REQUEST_ID)).isNull();
        assertThat(MDC.get(MDC_CLIENT_EVENT_ID)).isNull();
        assertThat(MDC.get(MDC_PRINCIPAL_ID)).isNull();
    }

    // -------------------------------------------------------------------
    // Response header echo
    // -------------------------------------------------------------------

    @Test
    void traceIdIsEchoedInResponseHeader() throws ServletException, IOException {
        var request  = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "tid-echo");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("tid-echo");
    }

    @Test
    void generatedTraceIdIsEchoedWhenHeaderAbsent() throws ServletException, IOException {
        var request  = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        String echoed = response.getHeader("X-Trace-Id");
        assertThat(echoed).isNotNull().isNotBlank();
        assertThat(UUID.fromString(echoed)).isNotNull();
    }
}
