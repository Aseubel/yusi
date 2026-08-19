package com.aseubel.yusi.observability.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class YusiMetricsTest {

    private static final Set<String> FORBIDDEN_VALUES = Set.of(
            "fixture-user-metrics", "fixture-query-metrics", "fixture-request-metrics");

    @Test
    void searchTagsAreAClosedLowCardinalityAllowlist() {
        assertThat(YusiMetrics.allowedSearchTags())
                .containsExactly("tool", "operation", "result", "failure_category");
        assertThat(YusiMetrics.allowedSearchTags())
                .doesNotContain("userId", "query", "requestId", "traceId");
    }

    @Test
    void recordsMetersWithoutPropagatingDynamicOrSensitiveValues() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        YusiMetrics metrics = new YusiMetrics(registry);

        metrics.recordToolSearch("diary", "search", "success", "none", 17L, 2);
        metrics.recordToolSearch("fixture-user-metrics", "fixture-query-metrics",
                "fixture-request-metrics", "fixture-request-metrics", 21L, 4);
        metrics.recordModelCall("success", "none", 9L);
        metrics.recordTask("weekly-match", "success");

        assertThat(registry.find("tool_search_total").counter()).isNotNull();
        assertThat(registry.find("tool_search_failure_total").counter()).isNotNull();
        assertThat(registry.find("tool_search_latency").timer()).isNotNull();
        assertThat(registry.find("tool_search_results").summary()).isNotNull();
        assertThat(registry.find("tool_search_total").counter().count()).isPositive();
        assertThat(registry.find("tool_search_latency").timer().count()).isPositive();
        assertThat(registry.find("tool_search_results").summary().count()).isPositive();
        assertThat(registry.getMeters()).isNotEmpty();

        for (Meter meter : registry.getMeters()) {
            assertThat(meter.getId().getTags())
                    .allMatch(tag -> YusiMetrics.allowedSearchTags().contains(tag.getKey()));
            assertThat(meter.getId().getTags())
                    .noneMatch(tag -> FORBIDDEN_VALUES.contains(tag.getValue()));
        }
    }
}
