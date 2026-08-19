package com.aseubel.yusi.observability.trace;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Bounded trace correlation helpers with strict MDC restoration. */
public final class TraceIdSupport {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER_NAME = "X-Trace-Id";
    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private TraceIdSupport() {
    }

    public static boolean isValid(String value) {
        return value != null && VALID_TRACE_ID.matcher(value).matches();
    }

    public static String acceptInbound(String value) {
        return isValid(value) ? value : UUID.randomUUID().toString();
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static void withTraceId(String traceId, Runnable action) {
        try (Scope ignored = open(traceId)) {
            action.run();
        }
    }

    public static <T> T withTraceId(String traceId, Supplier<T> action) {
        try (Scope ignored = open(traceId)) {
            return action.get();
        }
    }

    public static Scope open(String traceId) {
        return new Scope(acceptInbound(traceId));
    }

    public static final class Scope implements AutoCloseable {
        private final Map<String, String> previousContext;
        private boolean closed;

        private Scope(String traceId) {
            this.previousContext = MDC.getCopyOfContextMap();
            MDC.put(MDC_KEY, traceId);
        }

        public String traceId() {
            return MDC.get(MDC_KEY);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previousContext == null || previousContext.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(previousContext);
            }
        }
    }
}
