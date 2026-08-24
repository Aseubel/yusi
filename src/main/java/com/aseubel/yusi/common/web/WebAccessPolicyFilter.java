package com.aseubel.yusi.common.web;

import com.aseubel.yusi.service.web.RuntimeAccessPolicyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Supplier;

/** Enforces the runtime IP policy before authentication and controller execution. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebAccessPolicyFilter extends OncePerRequestFilter {

    private final Supplier<RuntimeAccessPolicySnapshot> policySupplier;
    private final ClientIpResolver clientIpResolver;
    private final RuntimeAccessPolicyEvaluator evaluator;

    @Autowired
    public WebAccessPolicyFilter(RuntimeAccessPolicyService policyService, ClientIpResolver clientIpResolver) {
        this(policyService::getEffectivePolicy, clientIpResolver, new RuntimeAccessPolicyEvaluator());
    }

    WebAccessPolicyFilter(Supplier<RuntimeAccessPolicySnapshot> policySupplier, ClientIpResolver clientIpResolver) {
        this(policySupplier, clientIpResolver, new RuntimeAccessPolicyEvaluator());
    }

    WebAccessPolicyFilter(Supplier<RuntimeAccessPolicySnapshot> policySupplier,
            ClientIpResolver clientIpResolver, RuntimeAccessPolicyEvaluator evaluator) {
        this.policySupplier = Objects.requireNonNull(policySupplier, "policySupplier");
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request == null ? null : request.getRequestURI();
        return !isProtectedPath(requestUri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        RuntimeAccessPolicySnapshot policy = policySupplier.get();
        String clientIp = clientIpResolver.resolve(request);
        if (!evaluator.isIpAllowed(policy, clientIp, LocalDateTime.now())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"info\":\"Access denied\",\"data\":null}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isProtectedPath(String requestUri) {
        if (requestUri == null) {
            return false;
        }
        return requestUri.equals("/api")
                || requestUri.startsWith("/api/")
                || requestUri.equals("/ws-chat")
                || requestUri.startsWith("/ws-chat/")
                || requestUri.equals("/ws-diary-voice")
                || requestUri.startsWith("/ws-diary-voice/");
    }
}
