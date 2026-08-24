package com.aseubel.yusi.repository;

import com.aseubel.yusi.common.constant.ModelCallStatus;
import com.aseubel.yusi.pojo.dto.model.ModelMetricAggregate;
import com.aseubel.yusi.pojo.dto.model.ModelMetricBucket;
import com.aseubel.yusi.pojo.dto.model.ModelMetricTrendQuery;
import com.aseubel.yusi.pojo.entity.ModelCallTrace;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Repository
@RequiredArgsConstructor
public class ModelCallTraceMetricsRepositoryImpl implements ModelCallTraceMetricsRepository {

    private static final DateTimeFormatter BUCKET_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Collection<String> SUCCESS_CODES = List.of("SUCCESS", "SUCCEEDED", "COMPLETED", "OK");

    @PersistenceContext
    private final EntityManager entityManager;

    @Override
    public ModelMetricAggregate aggregate(Specification<ModelCallTrace> specification) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<ModelCallTrace> root = query.from(ModelCallTrace.class);
        applySpecification(specification, root, query, builder);

        Expression<Integer> fallbackFlag = builder.<Integer>selectCase()
                .when(builder.isTrue(root.get("fallbackUsed")), 1)
                .otherwise(0);
        Expression<Integer> successFlag = builder.<Integer>selectCase()
                .when(successPredicate(builder, root), 1)
                .otherwise(0);
        Expression<Integer> rateLimitedFlag = builder.<Integer>selectCase()
                .when(rateLimitedPredicate(builder, root), 1)
                .otherwise(0);
        Expression<Integer> unknownCostFlag = builder.<Integer>selectCase()
                .when(builder.isNull(root.get("cost")), 1)
                .otherwise(0);

        query.multiselect(
                builder.count(root).alias("callCount"),
                builder.sum(fallbackFlag).alias("fallbackCount"),
                builder.sum(successFlag).alias("successCount"),
                builder.sum(rateLimitedFlag).alias("rateLimitedCount"),
                builder.sum(unknownCostFlag).alias("unknownCostCount"),
                builder.avg(root.<Long>get("latencyMs")).alias("averageLatencyMs"),
                builder.sum(root.<Long>get("inputTokens")).alias("inputTokens"),
                builder.sum(root.<Long>get("outputTokens")).alias("outputTokens"),
                builder.sum(root.<BigDecimal>get("cost")).alias("knownCost"));

        Tuple tuple = entityManager.createQuery(query).getSingleResult();
        long callCount = number(tuple, "callCount");
        long fallbackCount = number(tuple, "fallbackCount");
        long successCount = number(tuple, "successCount");
        long errorCount = Math.max(0L, callCount - successCount);
        long inputTokens = number(tuple, "inputTokens");
        long outputTokens = number(tuple, "outputTokens");
        return new ModelMetricAggregate(
                callCount,
                fallbackCount,
                rate(callCount, fallbackCount),
                rate(callCount, successCount),
                decimal(tuple, "averageLatencyMs"),
                percentile95(specification),
                number(tuple, "rateLimitedCount"),
                errorCount,
                inputTokens,
                outputTokens,
                decimalCost(tuple, "knownCost"),
                number(tuple, "unknownCostCount"));
    }

    @Override
    public List<ModelMetricBucket> aggregateTrend(Specification<ModelCallTrace> specification,
            ModelMetricTrendQuery.Bucket bucket) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<ModelCallTrace> root = query.from(ModelCallTrace.class);
        applySpecification(specification, root, query, builder);

        boolean h2 = isH2Dialect();
        String pattern = h2
                ? (bucket == ModelMetricTrendQuery.Bucket.DAY
                        ? "yyyy-MM-dd 00:00:00" : "yyyy-MM-dd HH:00:00")
                : (bucket == ModelMetricTrendQuery.Bucket.DAY
                        ? "%Y-%m-%d 00:00:00" : "%Y-%m-%d %H:00:00");
        Expression<String> bucketExpression = builder.function(
                h2 ? "FORMATDATETIME" : "DATE_FORMAT", String.class,
                root.get("createdAt"), builder.literal(pattern));
        Expression<Integer> successFlag = builder.<Integer>selectCase()
                .when(successPredicate(builder, root), 1)
                .otherwise(0);
        Expression<Integer> fallbackFlag = builder.<Integer>selectCase()
                .when(builder.isTrue(root.get("fallbackUsed")), 1)
                .otherwise(0);

        query.multiselect(
                        bucketExpression.alias("bucketStart"),
                        builder.count(root).alias("callCount"),
                        builder.sum(successFlag).alias("successCount"),
                        builder.sum(fallbackFlag).alias("fallbackCount"),
                        builder.sum(root.<Long>get("inputTokens")).alias("inputTokens"),
                        builder.sum(root.<Long>get("outputTokens")).alias("outputTokens"),
                        builder.avg(root.<Long>get("latencyMs")).alias("averageLatencyMs"))
                .groupBy(bucketExpression)
                .orderBy(builder.asc(bucketExpression));

        return entityManager.createQuery(query).getResultList().stream().map(tuple -> {
            long callCount = number(tuple, "callCount");
            long successCount = number(tuple, "successCount");
            return new ModelMetricBucket(
                    parseBucket(tuple.get("bucketStart")),
                    callCount,
                    successCount,
                    Math.max(0L, callCount - successCount),
                    number(tuple, "fallbackCount"),
                    number(tuple, "inputTokens"),
                    number(tuple, "outputTokens"),
                    decimal(tuple, "averageLatencyMs"));
        }).toList();
    }

    private Double percentile95(Specification<ModelCallTrace> specification) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<ModelCallTrace> countRoot = countQuery.from(ModelCallTrace.class);
        Predicate base = specification == null ? null : specification.toPredicate(countRoot, countQuery, builder);
        Predicate validLatency = builder.and(
                builder.isNotNull(countRoot.get("latencyMs")),
                builder.greaterThanOrEqualTo(countRoot.<Long>get("latencyMs"), 0L));
        countQuery.select(builder.count(countRoot));
        countQuery.where(base == null ? validLatency : builder.and(base, validLatency));
        long sampleCount = entityManager.createQuery(countQuery).getSingleResult();
        if (sampleCount < 20) return null;

        CriteriaQuery<Long> valueQuery = builder.createQuery(Long.class);
        Root<ModelCallTrace> valueRoot = valueQuery.from(ModelCallTrace.class);
        Predicate valueBase = specification == null ? null : specification.toPredicate(valueRoot, valueQuery, builder);
        Predicate valueLatency = builder.and(
                builder.isNotNull(valueRoot.get("latencyMs")),
                builder.greaterThanOrEqualTo(valueRoot.<Long>get("latencyMs"), 0L));
        valueQuery.select(valueRoot.get("latencyMs"));
        valueQuery.where(valueBase == null ? valueLatency : builder.and(valueBase, valueLatency));
        valueQuery.orderBy(builder.asc(valueRoot.get("latencyMs")));
        int position = (int) Math.max(0L, (long) Math.ceil(sampleCount * 0.95D) - 1L);
        TypedQuery<Long> typedQuery = entityManager.createQuery(valueQuery);
        return typedQuery.setFirstResult(position).setMaxResults(1).getResultStream()
                .findFirst().map(Long::doubleValue).orElse(null);
    }

    private void applySpecification(Specification<ModelCallTrace> specification, Root<ModelCallTrace> root,
            CriteriaQuery<?> query, CriteriaBuilder builder) {
        if (specification == null) return;
        Predicate predicate = specification.toPredicate(root, query, builder);
        if (predicate != null) query.where(predicate);
    }

    private Predicate successPredicate(CriteriaBuilder builder, Root<ModelCallTrace> root) {
        return builder.upper(root.<String>get("status")).in(SUCCESS_CODES);
    }

    private Predicate rateLimitedPredicate(CriteriaBuilder builder, Root<ModelCallTrace> root) {
        Expression<String> status = builder.upper(root.<String>get("status"));
        Expression<String> errorCode = builder.upper(root.<String>get("errorCode"));
        return builder.or(
                builder.like(status, "%429%"),
                builder.like(status, "%RATE%"),
                builder.like(errorCode, "%429%"),
                builder.like(errorCode, "%RATE%"));
    }

    private long number(Tuple tuple, String alias) {
        Number value = tuple.get(alias, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private double decimal(Tuple tuple, String alias) {
        Number value = tuple.get(alias, Number.class);
        return value == null ? 0D : value.doubleValue();
    }

    private BigDecimal decimalCost(Tuple tuple, String alias) {
        BigDecimal value = tuple.get(alias, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private LocalDateTime parseBucket(Object value) {
        if (value instanceof LocalDateTime localDateTime) return localDateTime;
        return LocalDateTime.parse(String.valueOf(value), BUCKET_FORMAT);
    }

    private boolean isH2Dialect() {
        Object dialect = entityManager.getEntityManagerFactory().getProperties().get("hibernate.dialect");
        return dialect != null && dialect.toString().toLowerCase(Locale.ROOT).contains("h2");
    }

    private double rate(long total, long count) {
        return total == 0L ? 0D : (double) count / total;
    }
}
