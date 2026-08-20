package com.aseubel.yusi.observability.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** Asynchronous Feishu transport with fixed payload and failure classifications. */
@Slf4j
public class FeishuAlertNotifier {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final FeishuAlertProperties properties;
    private final WebhookClient client;
    private final Executor executor;
    private final Clock clock;

    public FeishuAlertNotifier(FeishuAlertProperties properties, WebhookClient client,
            Executor executor, Clock clock) {
        this.properties = properties == null
                ? new FeishuAlertProperties(false, "", "") : properties;
        this.client = client;
        this.executor = executor == null ? Runnable::run : executor;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public void notify(AlertMessage message) {
        if (message == null || !properties.configured() || client == null) {
            return;
        }
        try {
            executor.execute(() -> deliver(message));
        } catch (RejectedExecutionException exception) {
            log.warn("Feishu alert delivery skipped: category=queue_full, attempt=0, exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    public static String renderPayload(AlertMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("alert message is required");
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("msg_type", "text");
        envelope.put("content", Map.of("text", renderText(message)));
        try {
            return JSON.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("alert payload construction failed", exception);
        }
    }

    private void deliver(AlertMessage message) {
        String payload = renderPayload(message);
        Instant timestamp = clock.instant();
        for (int attempt = 1; attempt <= properties.maxDeliveryAttempts(); attempt++) {
            try {
                client.send(new WebhookRequest(properties.webhookUrl(), payload,
                        sign(timestamp, properties.signingSecret()), true));
                return;
            } catch (RuntimeException exception) {
                String category = classify(exception);
                log.warn("Feishu alert delivery failed: category={}, attempt={}, backoffClass={}, exceptionType={}",
                        category, attempt, backoffClass(attempt), exception.getClass().getSimpleName());
                if (attempt < properties.maxDeliveryAttempts() && !sleepBackoff(attempt)) {
                    return;
                }
            }
        }
    }

    private boolean sleepBackoff(int attempt) {
        long delayMillis = 25L << Math.min(2, Math.max(0, attempt - 1));
        try {
            Thread.sleep(delayMillis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Feishu alert delivery stopped: category=interrupted, attempt={}, backoffClass={}, exceptionType={}",
                    attempt, backoffClass(attempt), exception.getClass().getSimpleName());
            return false;
        }
    }

    private String sign(Instant timestamp, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp.getEpochSecond() + "\n" + secret)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception exception) {
            return "invalid";
        }
    }

    private String classify(RuntimeException exception) {
        String type = exception.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if (type.contains("timeout")) {
            return "timeout";
        }
        if (type.contains("connect") || type.contains("socket")) {
            return "connection_failure";
        }
        return "http_failure";
    }

    private String backoffClass(int attempt) {
        return attempt <= 1 ? "short" : attempt == 2 ? "medium" : "long";
    }

    private static String renderText(AlertMessage message) {
        return "alert_category=" + message.category()
                + "\nservice=" + message.service()
                + "\noperation=" + message.operation()
                + "\nlevel=" + message.level()
                + "\nwindow=" + message.window()
                + "\ncount=" + message.count()
                + "\nvalue=" + message.value()
                + "\nclassification=" + message.classification()
                + "\nobserved_at=" + message.observedAt()
                + "\nstate=" + message.state();
    }

    public interface WebhookClient {
        void send(WebhookRequest request);
    }

    public record WebhookRequest(String destination, String payload, String signature, boolean signaturePresent) {
        @Override
        public String toString() {
            return "WebhookRequest[payloadPresent=" + (payload != null)
                    + ", signaturePresent=" + signaturePresent + "]";
        }
    }
}
