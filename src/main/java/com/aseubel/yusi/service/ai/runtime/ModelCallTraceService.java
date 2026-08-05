package com.aseubel.yusi.service.ai.runtime;

import com.aseubel.yusi.pojo.entity.ModelCallTrace;
import com.aseubel.yusi.repository.ModelCallTraceRepository;
import com.aseubel.yusi.service.ai.model.ModelUsageSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelCallTraceService {

    private final ModelCallTraceRepository traceRepository;
    private final AtomicLong persistenceFailureCount = new AtomicLong();

    @EventListener
    public void persist(ModelCallAttemptEvent event) {
        if (event == null) {
            return;
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

    private ModelCallTrace toEntity(ModelCallAttemptEvent event) {
        ModelUsageSnapshot usage = event.usage();
        return ModelCallTrace.builder()
                .requestId(event.requestId())
                .attemptId(event.attemptId())
                .runId(event.runId())
                .userId(event.userId())
                .scene(event.scene() == null ? "unknown" : event.scene())
                .language(event.language())
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
