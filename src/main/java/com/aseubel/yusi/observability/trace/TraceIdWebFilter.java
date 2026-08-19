package com.aseubel.yusi.observability.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Adds bounded trace correlation to HTTP and SSE requests. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdWebFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = TraceIdSupport.acceptInbound(request.getHeader(TraceIdSupport.HEADER_NAME));
        response.setHeader(TraceIdSupport.HEADER_NAME, traceId);
        try (TraceIdSupport.Scope ignored = TraceIdSupport.open(traceId)) {
            filterChain.doFilter(request, response);
        } finally {
            response.setHeader(TraceIdSupport.HEADER_NAME, traceId);
        }
    }
}
