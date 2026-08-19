package com.aseubel.yusi.observability.trace;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/** Adds and cleans trace correlation around STOMP inbound message handling. */
@Component
public class TraceIdWebSocketInterceptor implements ChannelInterceptor {

    private final ThreadLocal<Deque<Map<String, String>>> previousContexts =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        String traceId = TraceIdSupport.acceptInbound(
                accessor.getFirstNativeHeader(TraceIdSupport.HEADER_NAME));
        previousContexts.get().push(MDCContext.copy());
        accessor.setNativeHeader(TraceIdSupport.HEADER_NAME, traceId);
        MDCContext.put(traceId);
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel,
            boolean sent, Exception exception) {
        Deque<Map<String, String>> contexts = previousContexts.get();
        Map<String, String> previous = contexts.isEmpty() ? null : contexts.pop();
        MDCContext.restore(previous);
        if (contexts.isEmpty()) {
            previousContexts.remove();
        }
    }

    private static final class MDCContext {
        private MDCContext() {
        }

        private static Map<String, String> copy() {
            Map<String, String> context = org.slf4j.MDC.getCopyOfContextMap();
            return context == null ? Map.of() : context;
        }

        private static void put(String traceId) {
            org.slf4j.MDC.put(TraceIdSupport.MDC_KEY, traceId);
        }

        private static void restore(Map<String, String> previous) {
            if (previous == null || previous.isEmpty()) {
                org.slf4j.MDC.clear();
            } else {
                org.slf4j.MDC.setContextMap(previous);
            }
        }
    }
}
