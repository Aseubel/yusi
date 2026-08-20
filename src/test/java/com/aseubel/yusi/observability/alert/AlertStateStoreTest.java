package com.aseubel.yusi.observability.alert;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertStateStoreTest {

    private static final Instant START = Instant.parse("2026-08-20T12:00:00Z");
    private static final String FINGERPRINT = "service_unavailable|yusi-backend|readiness|critical";

    @Test
    void sameFingerprintIsSuppressedForThirtyMinutesButCanFireAfterward() {
        AlertStateStore store = new InMemoryAlertStateStore(32);

        assertThat(store.claim(FINGERPRINT, START, Duration.ofMinutes(30))).isTrue();
        assertThat(store.claim(FINGERPRINT, START.plus(Duration.ofMinutes(29)), Duration.ofMinutes(30)))
                .isFalse();
        assertThat(store.claim(FINGERPRINT, START.plus(Duration.ofMinutes(30)), Duration.ofMinutes(30)))
                .isTrue();
    }

    @Test
    void recoveryIsEmittedOnlyOnceForAnActiveFingerprint() {
        AlertStateStore store = new InMemoryAlertStateStore(32);
        store.markFiring(FINGERPRINT, START);

        assertThat(store.markRecovered(FINGERPRINT, START.plus(Duration.ofMinutes(1)))).isTrue();
        assertThat(store.markRecovered(FINGERPRINT, START.plus(Duration.ofMinutes(2)))).isFalse();
    }

    @Test
    void fingerprintDoesNotChangeWithWindowTime() {
        AlertSignal signal = new AlertSignal(
                "model_failure_rate", "yusi-backend", "model_call", "warning", "5m",
                42, 0.238D, "timeout", START, "firing");

        assertThat(signal.fingerprint()).isEqualTo("model_failure_rate|yusi-backend|model_call|warning");
    }

    @Test
    void redisStateUsesOnlyFixedFingerprintSegmentsInItsKey() {
        RedissonClient client = mock(RedissonClient.class);
        RBucket<String> bucket = mock(RBucket.class);
        when(client.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);
        RedisAlertStateStore store = new RedisAlertStateStore(client);

        assertThat(store.claim("budget_denied|yusi-backend|model_admission|warning",
                START, Duration.ofMinutes(30))).isTrue();
        verify(client).getBucket(eq("yusi:alert:dedup:budget_denied:yusi-backend:model_admission:warning:claim"));
    }

    @Test
    void redisFailureBecomesLowSensitivityDedupStoreCategoryAndDoesNotThrow() {
        RedissonClient client = mock(RedissonClient.class);
        RBucket<String> bucket = mock(RBucket.class);
        when(client.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.setIfAbsent(anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("fixture-content-alert"));
        RedisAlertStateStore store = new RedisAlertStateStore(client);

        assertThat(store.claim("budget_denied|yusi-backend|model_admission|warning",
                START, Duration.ofMinutes(30))).isTrue();
        assertThat(store.claim("budget_denied|yusi-backend|model_admission|warning",
                START.plus(Duration.ofMinutes(1)), Duration.ofMinutes(30))).isFalse();
        assertThat(store.lastFailureCategory()).isEqualTo("dedup_store_unavailable");
    }
}
