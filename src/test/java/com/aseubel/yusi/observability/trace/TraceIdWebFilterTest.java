package com.aseubel.yusi.observability.trace;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TraceIdWebFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void injectsAndReturnsValidatedTraceId() throws ServletException, IOException {
        TraceIdWebFilter filter = new TraceIdWebFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdSupport.HEADER_NAME, "http-trace-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                assertThat(MDC.get(TraceIdSupport.MDC_KEY)).isEqualTo("http-trace-1"));

        assertThat(response.getHeader(TraceIdSupport.HEADER_NAME)).isEqualTo("http-trace-1");
        assertThat(MDC.get(TraceIdSupport.MDC_KEY)).isNull();
    }

    @Test
    void restoresMdcWhenDownstreamFails() {
        TraceIdWebFilter filter = new TraceIdWebFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(TraceIdSupport.MDC_KEY, "baseline-trace");

        assertThatThrownBy(() -> filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            assertThat(MDC.get(TraceIdSupport.MDC_KEY)).isNotEqualTo("baseline-trace");
            throw new ServletException("fixture-query-filter");
        })).isInstanceOf(ServletException.class);

        assertThat(MDC.get(TraceIdSupport.MDC_KEY)).isEqualTo("baseline-trace");
    }

    @Test
    void websocketInterceptorRestoresMdcAfterInboundMessage() {
        TraceIdWebSocketInterceptor interceptor = new TraceIdWebSocketInterceptor();
        MessageChannel channel = mock(MessageChannel.class);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setNativeHeader(TraceIdSupport.HEADER_NAME, "socket-trace-1");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MDC.put(TraceIdSupport.MDC_KEY, "baseline-trace");

        Message<?> enriched = interceptor.preSend(message, channel);

        assertThat(MDC.get(TraceIdSupport.MDC_KEY)).isEqualTo("socket-trace-1");
        assertThat(StompHeaderAccessor.wrap(enriched)
                .getFirstNativeHeader(TraceIdSupport.HEADER_NAME)).isEqualTo("socket-trace-1");

        interceptor.afterSendCompletion(enriched, channel, true, null);
        assertThat(MDC.get(TraceIdSupport.MDC_KEY)).isEqualTo("baseline-trace");
    }
}
