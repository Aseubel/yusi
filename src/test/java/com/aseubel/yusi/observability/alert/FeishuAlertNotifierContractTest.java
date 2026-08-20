package com.aseubel.yusi.observability.alert;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAlertNotifierContractTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-08-20T12:00:00Z");

    @Test
    void payloadContainsOnlyTheFixedTenSemanticFieldsAndNeverSensitiveSentinels() {
        AlertMessage message = new AlertMessage(
                "fixture-user-alert",
                "fixture-query-alert",
                "fixture-content-alert",
                "fixture-token-alert",
                "fixture-object-key-alert",
                "fixture-user-alert",
                "fixture-query-alert",
                "fixture-content-alert",
                OBSERVED_AT,
                "fixture-token-alert");

        String payload = FeishuAlertNotifier.renderPayload(message);

        assertThat(payload).contains("msg_type", "content", "alert_category", "service",
                "operation", "level", "window", "count", "value", "classification",
                "observed_at", "state");
        assertThat(payload).doesNotContain(
                "fixture-user-alert",
                "fixture-query-alert",
                "fixture-content-alert",
                "fixture-token-alert",
                "fixture-object-key-alert");
    }

    @Test
    void mockWebhookContractReceivesOneLowSensitivityRequestWithoutRealDeliveryClaim() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<FeishuAlertNotifier.WebhookRequest> request = new AtomicReference<>();
        Executor direct = Runnable::run;
        Map<String, String> environment = Map.of("YUSI_ALERT_FEISHU_ENABLED", "true");
        FeishuAlertProperties properties = FeishuAlertProperties.fromEnvironment(
                name -> environment.containsKey(name)
                        ? environment.get(name)
                        : UUID.randomUUID().toString());
        FeishuAlertNotifier notifier = new FeishuAlertNotifier(
                properties,
                received -> {
                    calls.incrementAndGet();
                    request.set(received);
                },
                direct,
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC));

        notifier.notify(new AlertMessage(
                "model_failure_rate", "yusi-backend", "model_call", "warning", "5m",
                "42", "0.238", "timeout", OBSERVED_AT, "firing"));

        assertThat(calls).hasValue(1);
        assertThat(request).isNotNull();
        assertThat(request.get().payload()).doesNotContain(
                "fixture-user-alert", "fixture-query-alert", "fixture-content-alert",
                "fixture-token-alert", "fixture-object-key-alert");
        assertThat(request.get().signaturePresent()).isTrue();
    }

    @Test
    void propertiesToStringDoesNotExposeRuntimeValues() {
        FeishuAlertProperties properties = FeishuAlertProperties.fromEnvironment(
                name -> name.endsWith("ENABLED") ? "true" : UUID.randomUUID().toString());

        assertThat(properties.toString()).doesNotContain("http", "secret", "webhook");
    }

    @Test
    void failedMockDeliveryUsesAtMostThreeAttemptsAndOnlyLowSensitivityRequestFacts() {
        AtomicInteger calls = new AtomicInteger();
        FeishuAlertProperties properties = FeishuAlertProperties.fromEnvironment(
                name -> name.endsWith("ENABLED") ? "true" : UUID.randomUUID().toString());
        FeishuAlertNotifier notifier = new FeishuAlertNotifier(
                properties,
                received -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("fixture-content-alert");
                },
                Runnable::run,
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC));

        notifier.notify(new AlertMessage(
                "budget_denied", "yusi-backend", "model_admission", "warning", "5m",
                "10", "10", "limit_exceeded", OBSERVED_AT, "firing"));

        assertThat(calls).hasValue(3);
    }

    @Test
    void fullQueueIsDroppedWithoutThrowingBackToTheCaller() {
        FeishuAlertProperties properties = FeishuAlertProperties.fromEnvironment(
                name -> name.endsWith("ENABLED") ? "true" : UUID.randomUUID().toString());
        Executor fullQueue = command -> {
            throw new RejectedExecutionException("fixture-queue-full");
        };
        FeishuAlertNotifier notifier = new FeishuAlertNotifier(
                properties, received -> { }, fullQueue, Clock.fixed(OBSERVED_AT, ZoneOffset.UTC));

        notifier.notify(new AlertMessage(
                "service_unavailable", "yusi-backend", "readiness", "critical", "2m",
                "1", "0", "unavailable", OBSERVED_AT, "firing"));
    }
}
