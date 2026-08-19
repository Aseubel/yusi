package com.aseubel.yusi.service.ai.runtime;

import com.aseubel.yusi.pojo.entity.ModelCallTrace;
import com.aseubel.yusi.observability.metrics.YusiMetrics;
import com.aseubel.yusi.repository.ModelCallTraceRepository;
import com.aseubel.yusi.service.ai.model.ModelUsageSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class ModelCallTraceService {

    private final ModelCallTraceRepository traceRepository;
    private final YusiMetrics metrics;
    private final AtomicLong persistenceFailureCount = new AtomicLong();

    public ModelCallTraceService(ModelCallTraceRepository traceRepository) {
        this(traceRepository, null);
    }

    @Autowired
    public ModelCallTraceService(ModelCallTraceRepository traceRepository, YusiMetrics metrics) {
        this.traceRepository = traceRepository;
        this.metrics = metrics;
    }

    @EventListener
    public void persist(ModelCallAttemptEvent event) {
        if (event == null) {
            return;
        }
        if (metrics != null) {
            metrics.recordModelCall(resultLabel(event.status()), failureCategory(event.errorCode()), event.latencyMs());
        }
        try {
            traceRepository.save(toEntity(event));
        } catch (RuntimeException exception) {
            persistenceFailureCount.incrementAndGet();
            log.warn("Failed to persist model call trace attemptId={}: {}",
                    event.attemptId(), exception.getMessage());
        }
    }

    public long persistenceFailureCount() {
        return persistenceFailureCount.get();
    }

    private String resultLabel(String status) {
        if ("SUCCESS".equalsIgnoreCase(status)) {
            return "success";
        }
        if ("REJECTED".equalsIgnoreCase(status)) {
            return "rejected";
        }
        return "failure";
    }

    private String failureCategory(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return "none";
        }
        String normalized = errorCode.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("timeout")) {
            return "timeout";
        }
        if (normalized.contains("reject")) {
            return "rejected";
        }
        return "unknown";
    }

    private ModelCallTrace toEntity(ModelCallAttemptEvent event) {
        ModelUsageSnapshot usage = event.usage();
        return ModelCallTrace.builder()
                .requestId(event.requestId())
                .attemptId(event.attemptId())
                .runId(event.runId())
                .userId(event.userId())
                .scene(event.scene() == null ? "unknown" : event.scene())
                .promptKey(event.promptKey())
                .promptVersion(event.promptVersion())
                .promptLocale(event.promptLocale())
                .policyId(event.policyId())
                .policyVersion(event.policyVersion())
                .routeReason(event.routeReason())
                .primaryTier(event.primaryTier())
                .selectedTier(event.selectedTier())
                .modelId(event.modelId())
                .provider(event.provider())
                .modelName(event.modelName())
                .inputTokens(usage == null ? null : usage.inputTokens())
                .outputTokens(usage == null ? null : usage.outputTokens())
                .cachedTokens(usage == null ? null : usage.cachedTokens())
                .cost(usage == null ? null : usage.cost())
                .priceVersion(usage == null ? null : usage.priceVersion())
                .usageSource(usage == null ? "unavailable" : usage.usageSource())
                .latencyMs(event.latencyMs())
                .ttftMs(event.ttftMs())
                .retryIndex(event.retryIndex())
                .fallbackUsed(event.fallbackUsed())
                .status(event.status())
                .errorCode(event.errorCode())
                .finishReason(event.finishReason())
                .build();
    }
}
