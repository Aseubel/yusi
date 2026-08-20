package com.aseubel.yusi.observability.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitedMetricContractTest {

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "tool", "operation", "result", "failure_category");
    private static final Set<String> SENTINELS = Set.of(
            "fixture-user-rate", "fixture-query-rate", "fixture-content-rate",
            "fixture-token-rate", "fixture-object-key-rate");

    @Test
    void rateLimitedCounterUsesExactlyTheFourLowSensitivityTagKeys() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        YusiMetrics metrics = new YusiMetrics(registry);
        Method method = YusiMetrics.class.getMethod(
                "recordRateLimited", String.class, String.class);

        method.invoke(metrics, "fixture-query-rate", "fixture-content-rate");

        assertThat(registry.find("rate_limited_total").counter()).isNotNull();
        for (Meter meter : registry.getMeters()) {
            assertThat(meter.getId().getName()).isEqualTo("rate_limited_total");
            assertThat(meter.getId().getTags())
                    .extracting(Tag::getKey)
                    .containsExactlyInAnyOrderElementsOf(ALLOWED_TAGS);
            assertThat(meter.getId().getTags())
                    .extracting(Tag::getValue)
                    .doesNotContainAnyElementsOf(SENTINELS);
        }
    }

    @Test
    void rateLimitedCounterIsSeparateFromBudgetDeniedCounter() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        YusiMetrics metrics = new YusiMetrics(registry);
        Method method = YusiMetrics.class.getMethod(
                "recordRateLimited", String.class, String.class);

        method.invoke(metrics, "operation", "limit_exceeded");
        metrics.recordBudgetDenied("LIMIT_EXCEEDED:fixture-user-rate");

        assertThat(registry.find("rate_limited_total").counter().count()).isEqualTo(1D);
        assertThat(registry.find("budget_denied_total").counter().count()).isEqualTo(1D);
        assertThat(registry.find("budget_denied_total").counter().getId().getTag("operation"))
                .isEqualTo("model_admission");
        assertThat(registry.toString()).doesNotContain(
                "fixture-user-rate", "fixture-query-rate", "fixture-content-rate",
                "fixture-token-rate", "fixture-object-key-rate");
    }

    @Test
    void rateLimitedCounterPreservesKnownRateLimitOperations() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        YusiMetrics metrics = new YusiMetrics(registry);
        Method method = YusiMetrics.class.getMethod(
                "recordRateLimited", String.class, String.class);

        method.invoke(metrics, "plaza-submit", "limit_exceeded");
        method.invoke(metrics, "admin-embeddings-full-sync", "limit_exceeded");

        assertThat(registry.getMeters())
                .filteredOn(meter -> "rate_limited_total".equals(meter.getId().getName()))
                .extracting(meter -> meter.getId().getTag("operation"))
                .containsExactlyInAnyOrder("plaza-submit", "admin-embeddings-full-sync");
    }
}
