package com.aseubel.yusi.observability.trace;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.stereotype.Component;

/** Propagates bounded trace correlation through gRPC callbacks. */
@Component
@GrpcGlobalServerInterceptor
public class TraceIdGrpcInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> TRACE_HEADER = Metadata.Key.of(
            "x-trace-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        String traceId = TraceIdSupport.acceptInbound(headers.get(TRACE_HEADER));
        ServerCall<ReqT, RespT> forwardingCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void sendHeaders(Metadata responseHeaders) {
                responseHeaders.put(TRACE_HEADER, traceId);
                super.sendHeaders(responseHeaders);
            }
        };

        ServerCall.Listener<ReqT> delegate;
        try (TraceIdSupport.Scope ignored = TraceIdSupport.open(traceId)) {
            delegate = next.startCall(forwardingCall, headers);
        }

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onMessage(ReqT message) {
                TraceIdSupport.withTraceId(traceId, () -> super.onMessage(message));
            }

            @Override
            public void onHalfClose() {
                TraceIdSupport.withTraceId(traceId, super::onHalfClose);
            }

            @Override
            public void onCancel() {
                TraceIdSupport.withTraceId(traceId, super::onCancel);
            }

            @Override
            public void onComplete() {
                TraceIdSupport.withTraceId(traceId, super::onComplete);
            }

            @Override
            public void onReady() {
                TraceIdSupport.withTraceId(traceId, super::onReady);
            }
        };
    }
}
