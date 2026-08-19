package com.aseubel.yusi.observability.trace;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TraceIdSupportTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void acceptsOnlyBoundedHeaderTokens() {
        assertThat(TraceIdSupport.acceptInbound("trace-abc_123")).isEqualTo("trace-abc_123");
        assertThat(TraceIdSupport.acceptInbound("fixture-query\n")).isNotEqualTo("fixture-query\n");
        assertThat(TraceIdSupport.acceptInbound(" ")).isNotEqualTo(" ");
        assertThat(TraceIdSupport.isValid("x".repeat(129))).isFalse();
        assertThat(TraceIdSupport.isValid("trace-id")).isTrue();
    }

    @Test
    void restoresPreviousMdcAfterScopedExecution() {
        MDC.put(TraceIdSupport.MDC_KEY, "baseline-trace");

        TraceIdSupport.withTraceId("request-trace", () ->
                assertThat(MDC.get(TraceIdSupport.MDC_KEY)).isEqualTo("request-trace"));

        assertThat(MDC.get(TraceIdSupport.MDC_KEY)).isEqualTo("baseline-trace");
    }

    @Test
    void grpcInterceptorPropagatesTraceIdToCallSetupAndCallbacks() {
        TraceIdGrpcInterceptor interceptor = new TraceIdGrpcInterceptor();
        ServerCall<String, String> call = mock(ServerCall.class);
        Metadata.Key<String> key = Metadata.Key.of("x-trace-id", Metadata.ASCII_STRING_MARSHALLER);
        Metadata headers = new Metadata();
        headers.put(key, "grpc-trace-1");
        AtomicReference<String> setupTrace = new AtomicReference<>();
        AtomicReference<String> callbackTrace = new AtomicReference<>();
        ServerCallHandler<String, String> handler = (ignoredCall, ignoredHeaders) -> {
            setupTrace.set(MDC.get(TraceIdSupport.MDC_KEY));
            return new ServerCall.Listener<>() {
                @Override
                public void onHalfClose() {
                    callbackTrace.set(MDC.get(TraceIdSupport.MDC_KEY));
                }
            };
        };

        ServerCall.Listener<String> listener = interceptor.interceptCall(call, headers, handler);
        listener.onHalfClose();

        assertThat(setupTrace).hasValue("grpc-trace-1");
        assertThat(callbackTrace).hasValue("grpc-trace-1");
        assertThat(MDC.get(TraceIdSupport.MDC_KEY)).isNull();
    }
}
