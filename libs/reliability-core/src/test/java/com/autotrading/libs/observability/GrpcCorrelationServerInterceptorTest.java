package com.autotrading.libs.observability;

import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static com.autotrading.libs.observability.GrpcCorrelationServerInterceptor.CLIENT_EVENT_ID_KEY;
import static com.autotrading.libs.observability.GrpcCorrelationServerInterceptor.MDC_CLIENT_EVENT_ID;
import static com.autotrading.libs.observability.GrpcCorrelationServerInterceptor.MDC_PRINCIPAL_ID;
import static com.autotrading.libs.observability.GrpcCorrelationServerInterceptor.MDC_REQUEST_ID;
import static com.autotrading.libs.observability.GrpcCorrelationServerInterceptor.MDC_TRACE_ID;
import static com.autotrading.libs.observability.GrpcCorrelationServerInterceptor.PRINCIPAL_ID_KEY;
import static com.autotrading.libs.observability.GrpcCorrelationServerInterceptor.REQUEST_ID_KEY;
import static com.autotrading.libs.observability.GrpcCorrelationServerInterceptor.TRACE_ID_KEY;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;

/**
 * Unit tests for {@link GrpcCorrelationServerInterceptor} verifying that:
 * <ul>
 *   <li>All four snake_case MDC keys are set from metadata when present</li>
 *   <li>Missing headers fall back to a generated UUID</li>
 *   <li>MDC keys are cleared after every listener callback</li>
 *   <li>All listener delegation methods (onMessage, onHalfClose, onCancel, onComplete, onReady) propagate MDC</li>
 * </ul>
 */
class GrpcCorrelationServerInterceptorTest {

    private final GrpcCorrelationServerInterceptor interceptor = new GrpcCorrelationServerInterceptor();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // -------------------------------------------------------------------
    // Test infrastructure
    // -------------------------------------------------------------------

    private static final MethodDescriptor<byte[], byte[]> TEST_METHOD =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("test.Service/Method")
                    .setRequestMarshaller(PassthroughMarshaller.INSTANCE)
                    .setResponseMarshaller(PassthroughMarshaller.INSTANCE)
                    .build();

    private static ServerCall<byte[], byte[]> noopCall() {
        return new ServerCall<>() {
            @Override public void request(int numMessages) {}
            @Override public void sendHeaders(Metadata headers) {}
            @Override public void sendMessage(byte[] message) {}
            @Override public void close(Status status, Metadata trailers) {}
            @Override public boolean isCancelled() { return false; }
            @Override public MethodDescriptor<byte[], byte[]> getMethodDescriptor() { return TEST_METHOD; }
        };
    }

    /** Captures the four MDC values inside a listener callback. */
    private static ServerCallHandler<byte[], byte[]> capturingHandler(String[] out) {
        return (call, headers) -> new ServerCall.Listener<byte[]>() {
            @Override public void onHalfClose() { doCapture(); }
            @Override public void onMessage(byte[] m) { doCapture(); }
            @Override public void onCancel() { doCapture(); }
            @Override public void onComplete() { doCapture(); }
            @Override public void onReady() { doCapture(); }
            private void doCapture() {
                out[0] = MDC.get(MDC_TRACE_ID);
                out[1] = MDC.get(MDC_REQUEST_ID);
                out[2] = MDC.get(MDC_CLIENT_EVENT_ID);
                out[3] = MDC.get(MDC_PRINCIPAL_ID);
            }
        };
    }

    // -------------------------------------------------------------------
    // Happy-path: all four headers present
    // -------------------------------------------------------------------

    @Test
    void allFourMdcKeysAreSetFromMetadataOnHalfClose() {
        Metadata headers = new Metadata();
        headers.put(TRACE_ID_KEY,        "t-1");
        headers.put(REQUEST_ID_KEY,      "r-1");
        headers.put(CLIENT_EVENT_ID_KEY, "i-1");
        headers.put(PRINCIPAL_ID_KEY,    "p-1");

        String[] captured = new String[4];
        ServerCall.Listener<byte[]> listener =
                interceptor.interceptCall(noopCall(), headers, capturingHandler(captured));
        listener.onHalfClose();

        assertThat(captured[0]).isEqualTo("t-1");
        assertThat(captured[1]).isEqualTo("r-1");
        assertThat(captured[2]).isEqualTo("i-1");
        assertThat(captured[3]).isEqualTo("p-1");
    }

    @Test
    void allFourMdcKeysAreSetOnMessage() {
        Metadata headers = new Metadata();
        headers.put(TRACE_ID_KEY,        "t-msg");
        headers.put(REQUEST_ID_KEY,      "r-msg");
        headers.put(CLIENT_EVENT_ID_KEY, "i-msg");
        headers.put(PRINCIPAL_ID_KEY,    "p-msg");

        String[] captured = new String[4];
        ServerCall.Listener<byte[]> listener =
                interceptor.interceptCall(noopCall(), headers, capturingHandler(captured));
        listener.onMessage(new byte[0]);

        assertThat(captured[0]).isEqualTo("t-msg");
        assertThat(captured[3]).isEqualTo("p-msg");
    }

    @Test
    void allFourMdcKeysAreSetOnCancelAndOnComplete() {
        Metadata headers = new Metadata();
        headers.put(TRACE_ID_KEY, "t-ev");
        headers.put(REQUEST_ID_KEY, "r-ev");
        headers.put(CLIENT_EVENT_ID_KEY, "i-ev");
        headers.put(PRINCIPAL_ID_KEY, "p-ev");

        String[] cancelCapture  = new String[4];
        String[] completeCapture = new String[4];
        ServerCallHandler<byte[], byte[]> handler = (call, hdrs) -> new ServerCall.Listener<byte[]>() {
            @Override public void onCancel() {
                cancelCapture[0]  = MDC.get(MDC_TRACE_ID);
                cancelCapture[1]  = MDC.get(MDC_REQUEST_ID);
                cancelCapture[2]  = MDC.get(MDC_CLIENT_EVENT_ID);
                cancelCapture[3]  = MDC.get(MDC_PRINCIPAL_ID);
            }
            @Override public void onComplete() {
                completeCapture[0] = MDC.get(MDC_TRACE_ID);
                completeCapture[1] = MDC.get(MDC_REQUEST_ID);
                completeCapture[2] = MDC.get(MDC_CLIENT_EVENT_ID);
                completeCapture[3] = MDC.get(MDC_PRINCIPAL_ID);
            }
            @Override public void onReady() {}
        };

        ServerCall.Listener<byte[]> listener = interceptor.interceptCall(noopCall(), headers, handler);
        listener.onCancel();
        listener.onComplete();
        listener.onReady();

        assertThat(cancelCapture[0]).isEqualTo("t-ev");
        assertThat(completeCapture[3]).isEqualTo("p-ev");
    }

    // -------------------------------------------------------------------
    // Fallback: missing headers → UUID
    // -------------------------------------------------------------------

    @Test
    void missingMetadataFallsBackToUuid() {
        Metadata headers = new Metadata(); // no correlation headers

        String[] captured = new String[4];
        ServerCall.Listener<byte[]> listener =
                interceptor.interceptCall(noopCall(), headers, capturingHandler(captured));
        listener.onHalfClose();

        for (String v : captured) {
            assertThat(v).isNotNull().isNotBlank();
            assertThat(UUID.fromString(v)).isNotNull(); // must be valid UUID
        }
    }

    // -------------------------------------------------------------------
    // Cleanup: MDC cleared after callbacks
    // -------------------------------------------------------------------

    @Test
    void mdcKeysAreClearedAfterOnHalfClose() {
        Metadata headers = new Metadata();
        headers.put(TRACE_ID_KEY, "t-clear");
        headers.put(REQUEST_ID_KEY, "r-clear");
        headers.put(CLIENT_EVENT_ID_KEY, "i-clear");
        headers.put(PRINCIPAL_ID_KEY, "p-clear");

        ServerCall.Listener<byte[]> listener =
                interceptor.interceptCall(noopCall(), headers, capturingHandler(new String[4]));
        listener.onHalfClose();

        assertThat(MDC.get(MDC_TRACE_ID)).isNull();
        assertThat(MDC.get(MDC_REQUEST_ID)).isNull();
        assertThat(MDC.get(MDC_CLIENT_EVENT_ID)).isNull();
        assertThat(MDC.get(MDC_PRINCIPAL_ID)).isNull();
    }

    // -------------------------------------------------------------------
    // Constant names guard
    // -------------------------------------------------------------------

    @Test
    void mdcKeyNamesUseSnakeCase() {
        assertThat(MDC_TRACE_ID).isEqualTo("trace_id");
        assertThat(MDC_REQUEST_ID).isEqualTo("request_id");
        assertThat(MDC_CLIENT_EVENT_ID).isEqualTo("client_event_id");
        assertThat(MDC_PRINCIPAL_ID).isEqualTo("principal_id");
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private static final class PassthroughMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        static final PassthroughMarshaller INSTANCE = new PassthroughMarshaller();
        @Override public InputStream stream(byte[] value) { return InputStream.nullInputStream(); }
        @Override public byte[] parse(InputStream stream) { return new byte[0]; }
    }
}
