package com.aseubel.yusi.common.web;

import com.aseubel.yusi.service.web.RuntimeAccessPolicyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Applies the runtime Origin policy to native WebSocket and SockJS handshakes. */
@Component
public class RuntimeOriginHandshakeInterceptor implements HandshakeInterceptor {

    private final Supplier<RuntimeAccessPolicySnapshot> policySupplier;
    private final RuntimeAccessPolicyEvaluator evaluator;

    @Autowired
    public RuntimeOriginHandshakeInterceptor(RuntimeAccessPolicyService policyService) {
        this(policyService::getEffectivePolicy, new RuntimeAccessPolicyEvaluator());
    }

    RuntimeOriginHandshakeInterceptor(Supplier<RuntimeAccessPolicySnapshot> policySupplier) {
        this(policySupplier, new RuntimeAccessPolicyEvaluator());
    }

    RuntimeOriginHandshakeInterceptor(Supplier<RuntimeAccessPolicySnapshot> policySupplier,
            RuntimeAccessPolicyEvaluator evaluator) {
        this.policySupplier = Objects.requireNonNull(policySupplier, "policySupplier");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String origin = request == null ? null : request.getHeaders().getOrigin();
        if (evaluator.isOriginAllowed(policySupplier.get(), origin, LocalDateTime.now())) {
            return true;
        }
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // No state is kept for a completed handshake.
    }
}
