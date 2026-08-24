package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.dto.model.ModelMetricAggregate;
import com.aseubel.yusi.pojo.dto.model.ModelMetricBucket;
import com.aseubel.yusi.pojo.dto.model.ModelMetricTrendQuery;
import com.aseubel.yusi.pojo.entity.ModelCallTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(ModelCallTraceMetricsRepositoryImpl.class)
class ModelCallTraceMetricsRepositoryTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 8, 24, 10, 15);

    @Autowired
    private ModelCallTraceMetricsRepository metricsRepository;

    @Autowired
    private ModelCallTraceRepository traceRepository;

    @BeforeEach
    void cleanTraces() {
        traceRepository.deleteAll();
    }

    @Test
    void returnsZeroMetricsForEmptyDataset() {
        ModelMetricAggregate aggregate = metricsRepository.aggregate(allTraces());

        assertThat(aggregate.callCount()).isZero();
        assertThat(aggregate.inputTokens()).isZero();
        assertThat(aggregate.outputTokens()).isZero();
        assertThat(aggregate.knownCost()).isZero();
        assertThat(aggregate.p95LatencyMs()).isNull();
    }

    @Test
    void aggregatesTraceRowsWithoutLoadingEntities() {
        traceRepository.saveAll(List.of(
                trace("request-1", "SUCCESS", false, 100L, 50L, new BigDecimal("0.10"), 100L,
                        BASE_TIME),
                trace("request-2", "FAILED", true, 20L, 10L, null, 200L,
                        BASE_TIME.plusMinutes(30), "RATE_LIMITED"),
                trace("request-3", "SUCCEEDED", false, null, null, new BigDecimal("0.20"), 300L,
                        BASE_TIME.plusHours(1))));

        ModelMetricAggregate aggregate = metricsRepository.aggregate(allTraces());

        assertThat(aggregate.callCount()).isEqualTo(3);
        assertThat(aggregate.fallbackCount()).isEqualTo(1);
        assertThat(aggregate.fallbackRate()).isEqualTo(1D / 3D);
        assertThat(aggregate.successRate()).isEqualTo(2D / 3D);
        assertThat(aggregate.averageLatencyMs()).isEqualTo(200D);
        assertThat(aggregate.rateLimitedCount()).isEqualTo(1);
        assertThat(aggregate.errorCount()).isEqualTo(1);
        assertThat(aggregate.inputTokens()).isEqualTo(120);
        assertThat(aggregate.outputTokens()).isEqualTo(60);
        assertThat(aggregate.knownCost()).isEqualByComparingTo("0.30");
        assertThat(aggregate.unknownCostCount()).isEqualTo(1);
        assertThat(aggregate.p95LatencyMs()).isNull();
    }

    @Test
    void groupsTrendRowsByMySqlDateFormatBucket() {
        traceRepository.saveAll(List.of(
                trace("request-1", "SUCCESS", false, 100L, 50L, new BigDecimal("0.10"), 100L,
                        BASE_TIME),
                trace("request-2", "FAILED", true, 20L, 10L, null, 200L,
                        BASE_TIME.plusMinutes(30)),
                trace("request-3", "SUCCESS", false, 30L, 15L, new BigDecimal("0.20"), 300L,
                        BASE_TIME.plusHours(1).plusMinutes(5))));

        List<ModelMetricBucket> buckets = metricsRepository.aggregateTrend(allTraces(),
                ModelMetricTrendQuery.Bucket.HOUR);

        assertThat(buckets).hasSize(2);
        assertThat(buckets.get(0).bucketStart()).isEqualTo(BASE_TIME.withMinute(0));
        assertThat(buckets.get(0).callCount()).isEqualTo(2);
        assertThat(buckets.get(0).successCount()).isEqualTo(1);
        assertThat(buckets.get(0).errorCount()).isEqualTo(1);
        assertThat(buckets.get(0).fallbackCount()).isEqualTo(1);
        assertThat(buckets.get(1).bucketStart()).isEqualTo(BASE_TIME.plusHours(1).withMinute(0));
        assertThat(buckets.get(1).callCount()).isEqualTo(1);
    }

    private Specification<ModelCallTrace> allTraces() {
        return (root, query, builder) -> builder.conjunction();
    }

    private ModelCallTrace trace(String requestId, String status, boolean fallbackUsed,
            Long inputTokens, Long outputTokens, BigDecimal cost, Long latencyMs,
            LocalDateTime createdAt) {
        return trace(requestId, status, fallbackUsed, inputTokens, outputTokens, cost, latencyMs,
                createdAt, null);
    }

    private ModelCallTrace trace(String requestId, String status, boolean fallbackUsed,
            Long inputTokens, Long outputTokens, BigDecimal cost, Long latencyMs,
            LocalDateTime createdAt, String errorCode) {
        return ModelCallTrace.builder()
                .requestId(requestId)
                .attemptId("attempt-" + requestId)
                .scene("chat")
                .modelId("model-1")
                .provider("openai")
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .cost(cost)
                .latencyMs(latencyMs)
                .retryIndex(0)
                .fallbackUsed(fallbackUsed)
                .status(status)
                .errorCode(errorCode)
                .createdAt(createdAt)
                .build();
    }
}
