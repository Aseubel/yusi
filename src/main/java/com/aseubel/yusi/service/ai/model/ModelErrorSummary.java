package com.aseubel.yusi.service.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.HttpException;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Produces a bounded provider error description without retaining provider
 * response bodies or request content.
 */
public final class ModelErrorSummary {

    private static final int MAX_PROVIDER_BODY_LENGTH = 4096;
    private static final int MAX_TOKEN_LENGTH = 128;
    private static final int MAX_DETAIL_LENGTH = 256;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ModelErrorSummary() {
    }

    public static String summarize(Throwable error, Integer httpStatus) {
        StringJoiner summary = new StringJoiner(";");
        if (httpStatus != null) {
            summary.add("httpStatus=" + httpStatus);
        }

        Throwable first = firstThrowable(error);
        if (first != null) {
            summary.add("exceptionType=" + first.getClass().getSimpleName());
        }

        ProviderFields fields = providerFields(error);
        addToken(summary, "providerCode", fields.code());
        addToken(summary, "providerType", fields.type());
        addToken(summary, "providerParam", fields.param());

        String reason = firstNonBlank(fields.reason(), fields.message());
        if (reason != null) {
            summary.add("providerReason=" + classifyReason(reason));
        } else {
            summary.add("providerReason=provider_error");
        }
        if (first instanceof HttpException) {
            addDetail(summary, "providerDetail", fields.reason(), fields.message());
        }
        return summary.toString();
    }

    private static Throwable firstThrowable(Throwable error) {
        for (Throwable current : causes(error)) {
            if (current instanceof HttpException) {
                return current;
            }
        }
        for (Throwable current : causes(error)) {
            if (current != null && current.getClass().getSimpleName() != null) {
                return current;
            }
        }
        return null;
    }

    private static ProviderFields providerFields(Throwable error) {
        for (Throwable current : causes(error)) {
            String message = current.getMessage();
            if (message == null || message.isBlank()) {
                continue;
            }
            ProviderFields fields = parseJson(message);
            if (fields.hasValue()) {
                return fields;
            }
        }
        return new ProviderFields(null, null, null, null, firstMessage(error));
    }

    private static ProviderFields parseJson(String message) {
        String bounded = message.length() <= MAX_PROVIDER_BODY_LENGTH
                ? message : message.substring(0, MAX_PROVIDER_BODY_LENGTH);
        int start = bounded.indexOf('{');
        int end = bounded.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return new ProviderFields(null, null, null, null, null);
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(bounded.substring(start, end + 1));
            JsonNode errorNode = root.path("error");
            if (errorNode.isMissingNode() || errorNode.isNull()) {
                errorNode = root;
            }
            return new ProviderFields(
                    text(errorNode, "code"),
                    text(errorNode, "type"),
                    text(errorNode, "param"),
                    text(errorNode, "reason"),
                    text(errorNode, "message"));
        } catch (Exception ignored) {
            return new ProviderFields(null, null, null, null, null);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String firstMessage(Throwable error) {
        for (Throwable current : causes(error)) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
        }
        return null;
    }

    private static void addToken(StringJoiner summary, String name, String value) {
        String token = safeToken(value);
        if (token != null) {
            summary.add(name + "=" + token);
        }
    }

    private static void addDetail(StringJoiner summary, String name, String first, String second) {
        String detail = firstNonBlank(first, second);
        if (detail == null) {
            return;
        }
        String sanitized = sanitizeDetail(detail);
        if (sanitized != null) {
            summary.add(name + "=" + sanitized);
        }
    }

    private static String sanitizeDetail(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        normalized = normalized.replaceAll("(?i)(https?://|wss?://)[^ ]+", "<url>");
        normalized = normalized.replaceAll(
                "(?i)(api[_-]?key|authorization|bearer|token|secret|password)\\s*[:=]\\s*[^,; ]+",
                "$1=<redacted>");
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > MAX_DETAIL_LENGTH) {
            normalized = normalized.substring(0, MAX_DETAIL_LENGTH) + "...";
        }
        return normalized;
    }

    private static String safeToken(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_TOKEN_LENGTH) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.matches("[A-Za-z0-9][A-Za-z0-9_.:/\\[\\]-]*") ? trimmed : null;
    }

    private static String classifyReason(String value) {
        String reason = value.toLowerCase(Locale.ROOT);
        if (reason.contains("unsupported parameter") || reason.contains("unsupported field")
                || reason.contains("unknown parameter") || reason.contains("invalid parameter")) {
            return "unsupported_parameter";
        }
        if (reason.contains("not supported") || reason.contains("not support")) {
            return "unsupported";
        }
        if (reason.contains("invalid model") || reason.contains("model not found")) {
            return "invalid_model";
        }
        if (reason.contains("required") || reason.contains("missing")) {
            return "missing_parameter";
        }
        if (reason.contains("context") || reason.contains("token limit") || reason.contains("too many tokens")) {
            return "context_limit";
        }
        if (reason.contains("image") || reason.contains("vision")) {
            return "vision_input_rejected";
        }
        if (reason.contains("reasoning") || reason.contains("thinking")) {
            return "reasoning_input_rejected";
        }
        if (reason.contains("rate") || reason.contains("quota") || reason.contains("too many requests")) {
            return "quota_or_rate_limit";
        }
        if (reason.contains("timeout") || reason.contains("timed out")) {
            return "timeout";
        }
        if (reason.contains("json") || reason.contains("schema") || reason.contains("structured")) {
            return "structured_output";
        }
        if (reason.contains("invalid") || reason.contains("bad request") || reason.contains("malformed")) {
            return "invalid_request";
        }
        return "provider_error";
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first
                : second != null && !second.isBlank() ? second : null;
    }

    private static Iterable<Throwable> causes(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        java.util.List<Throwable> causes = new java.util.ArrayList<>();
        java.util.ArrayDeque<Throwable> pending = new java.util.ArrayDeque<>();
        if (error != null) {
            pending.add(error);
        }
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!seen.add(current)) {
                continue;
            }
            causes.add(current);
            if (current.getCause() != null) {
                pending.addLast(current.getCause());
            }
            for (Throwable suppressed : current.getSuppressed()) {
                if (suppressed != null) {
                    pending.addLast(suppressed);
                }
            }
        }
        return causes;
    }

    private record ProviderFields(String code, String type, String param, String reason, String message) {
        private boolean hasValue() {
            return code != null || type != null || param != null || reason != null || message != null;
        }
    }
}
