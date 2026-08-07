package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.pojo.dto.model.ModelCallTraceItem;
import com.aseubel.yusi.pojo.dto.model.ModelCallTraceQuery;
import com.aseubel.yusi.pojo.dto.model.ModelGovernanceSnapshot;
import com.aseubel.yusi.pojo.dto.model.ModelGovernanceUpdateRequest;
import com.aseubel.yusi.pojo.dto.model.ModelMetricSummary;
import com.aseubel.yusi.pojo.dto.model.ModelRoutePreviewRequest;
import com.aseubel.yusi.pojo.dto.model.ModelRoutePreviewResponse;
import com.aseubel.yusi.pojo.entity.ModelCallTrace;
import com.aseubel.yusi.repository.ModelCallTraceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModelManagementService {

    private final ModelStateCenter modelStateCenter;
    private final ModelConfigCenter modelConfigCenter;
    private final ModelRouterService modelRouterService;
    private final ModelInstanceRegistry modelInstanceRegistry;
    private final ModelCallTraceRepository modelCallTraceRepository;

    public List<ModelRuntimeState> listModelStates() {
        return modelStateCenter.listStates();
    }

    public ModelGovernanceSnapshot getGovernanceSnapshot() {
        ModelRoutingProperties config = modelConfigCenter.getEffectiveConfig();
        List<ModelRuntimeState> runtimeStates = safeStates();
        Map<String, ModelRuntimeState> stateById = runtimeStates.stream()
                .filter(Objects::nonNull)
                .filter(state -> state.getInstanceId() != null)
                .collect(Collectors.toMap(ModelRuntimeState::getInstanceId, state -> state,
                        (first, ignored) -> first));

        List<ModelGovernanceSnapshot.ModelGovernanceModel> models = safeModels(config).stream()
                .map(model -> toGovernanceModel(model, stateById))
                .toList();
        List<ModelGovernanceSnapshot.ModelGovernanceTier> tiers = safeTiers(config).entrySet().stream()
                .map(entry -> toGovernanceTier(entry.getKey(), entry.getValue(), stateById))
                .toList();
        ModelMetricSummary summary = getMetrics(ModelCallTraceQuery.builder().build());

        return ModelGovernanceSnapshot.builder()
                .version(config.getVersion())
                .schemaVersion(config.getSchemaVersion())
                .defaultLanguage(config.getDefaultLanguage())
                .defaultScene(config.getDefaultScene())
                .defaultTier(config.getDefaultTier())
                .models(models)
                .tiers(tiers)
                .routes(config.getRoutes() == null ? List.of() : config.getRoutes())
                .defaultRoute(config.getDefaultRoute())
                .runtimeStates(runtimeStates)
                .summary(summary)
                .build();
    }

    public long updateGovernance(ModelGovernanceUpdateRequest request, String operatorId) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "模型治理配置不能为空");
        }
        ModelRoutingProperties updated = modelConfigCenter.updateCanonical(
                request.toProperties(), request.getExpectedVersion(), operatorId);
        return updated.getVersion();
    }

    public ModelRoutePreviewResponse previewRoute(ModelRoutePreviewRequest request) {
        if (request == null || isBlank(request.getScene())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "scene 不能为空");
        }
        ModelRouteContext context = ModelRouteContext.builder()
                .language(request.getLanguage())
                .scene(request.getScene())
                .riskLevel(request.getRiskLevel())
                .estimatedInputTokens(request.getEstimatedInputTokens())
                .reservedOutputTokens(request.getReservedOutputTokens())
                .build();
        final ModelRouteDecision decision;
        try {
            decision = modelRouterService.plan(context);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, exception.getMessage());
        }

        List<ModelRoutePreviewResponse.Candidate> candidates = decision.candidates().stream()
                .map(candidate -> ModelRoutePreviewResponse.Candidate.builder()
                        .tierId(candidate.tierId())
                        .modelId(candidate.modelId())
                        .provider(candidate.provider())
                        .modelName(candidate.modelName())
                        .available(candidate.available())
                        .excludedReason(candidate.excludedReason())
                        .build())
                .toList();
        Set<String> warnings = new LinkedHashSet<>();
        decision.candidates().stream()
                .filter(candidate -> candidate.excludedReason() != null
                        && !"fallback-tier".equals(candidate.excludedReason()))
                .forEach(candidate -> warnings.add("excluded:" + candidate.modelId()
                        + ":" + candidate.excludedReason()));
        decision.candidates().stream()
                .filter(candidate -> candidate.available())
                .map(ModelRouteCandidate::modelId)
                .map(modelInstanceRegistry::getById)
                .flatMap(java.util.Optional::stream)
                .filter(instance -> instance.getInputPricePerMillion() == null
                        || instance.getOutputPricePerMillion() == null)
                .forEach(instance -> warnings.add("unknown-cost:" + instance.getId()));
        if (decision.attemptCandidates().isEmpty()) {
            warnings.add("no-available-candidate");
        }
        return ModelRoutePreviewResponse.builder()
                .policyId(decision.policyId())
                .primaryTier(decision.primaryTier())
                .candidates(candidates)
                .routeReason(decision.routeReason())
                .warnings(new ArrayList<>(warnings))
                .build();
    }

    public Page<ModelCallTraceItem> queryAttempts(ModelCallTraceQuery query) {
        ModelCallTraceQuery safeQuery = query == null ? ModelCallTraceQuery.builder().build() : query;
        if (modelCallTraceRepository == null) {
            return Page.empty(pageable(safeQuery));
        }
        return modelCallTraceRepository.findAll(buildSpecification(safeQuery), pageable(safeQuery))
                .map(ModelCallTraceItem::from);
    }

    public ModelMetricSummary getMetrics(ModelCallTraceQuery query) {
        ModelCallTraceQuery safeQuery = query == null ? ModelCallTraceQuery.builder().build() : query;
        if (modelCallTraceRepository == null) {
            return emptyMetrics();
        }
        List<ModelCallTrace> traces = modelCallTraceRepository.findAll(buildSpecification(safeQuery));
        if (traces.isEmpty()) {
            return emptyMetrics();
        }

        long fallbackCount = traces.stream().filter(trace -> Boolean.TRUE.equals(trace.getFallbackUsed())).count();
        long successCount = traces.stream().filter(this::isSuccess).count();
        long rateLimitedCount = traces.stream().filter(this::isRateLimited).count();
        long errorCount = traces.stream().filter(trace -> !isSuccess(trace)).count();
        List<Long> latencies = traces.stream()
                .map(ModelCallTrace::getLatencyMs)
                .filter(Objects::nonNull)
                .filter(value -> value >= 0)
                .sorted()
                .toList();
        double averageLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0D);
        Double p95 = latencies.size() >= 20 ? percentile(latencies, 0.95D) : null;
        long inputTokens = traces.stream().map(ModelCallTrace::getInputTokens)
                .filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        long outputTokens = traces.stream().map(ModelCallTrace::getOutputTokens)
                .filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        BigDecimal knownCost = traces.stream().map(ModelCallTrace::getCost)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        long unknownCostCount = traces.stream().filter(trace -> trace.getCost() == null).count();

        return ModelMetricSummary.builder()
                .routeCount(traces.size())
                .fallbackCount(fallbackCount)
                .fallbackRate(rate(traces.size(), fallbackCount))
                .successRate(rate(traces.size(), successCount))
                .averageLatencyMs(averageLatency)
                .p95LatencyMs(p95)
                .rateLimitedCount(rateLimitedCount)
                .errorCount(errorCount)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .knownCost(knownCost)
                .unknownCostCount(unknownCostCount)
                .build();
    }

    private Specification<ModelCallTrace> buildSpecification(ModelCallTraceQuery query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (query.getFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), query.getFrom()));
            }
            if (query.getTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), query.getTo()));
            }
            addLike(predicates, criteriaBuilder, root, "scene", query.getScene());
            addLike(predicates, criteriaBuilder, root, "language", query.getLanguage());
            addLike(predicates, criteriaBuilder, root, "userId", query.getUserId());
            addLike(predicates, criteriaBuilder, root, "tenantId", query.getTenantId());
            addLike(predicates, criteriaBuilder, root, "selectedTier", query.getModelTier());
            addLike(predicates, criteriaBuilder, root, "provider", query.getProvider());
            addLike(predicates, criteriaBuilder, root, "modelId", query.getModel());
            addLike(predicates, criteriaBuilder, root, "status", query.getStatus());
            if (query.getFallbackUsed() != null) {
                predicates.add(criteriaBuilder.equal(root.get("fallbackUsed"), query.getFallbackUsed()));
            }
            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private void addLike(List<jakarta.persistence.criteria.Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            jakarta.persistence.criteria.Root<ModelCallTrace> root,
            String field, String value) {
        if (!isBlank(value)) {
            predicates.add(criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get(field)), value.trim().toLowerCase(Locale.ROOT)));
        }
    }

    private Pageable pageable(ModelCallTraceQuery query) {
        int page = Math.max(0, query.getPage());
        int size = Math.min(100, Math.max(1, query.getSize()));
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private ModelGovernanceSnapshot.ModelGovernanceModel toGovernanceModel(
            ModelRoutingProperties.ModelDefinition model, Map<String, ModelRuntimeState> stateById) {
        ModelRoutingProperties.PricingDefinition pricing = model.getPricing();
        String endpoint = sanitizeEndpoint(model.getBaseurl());
        return ModelGovernanceSnapshot.ModelGovernanceModel.builder()
                .id(model.getId())
                .displayName(isBlank(model.getDisplayName()) ? model.getId() : model.getDisplayName())
                .provider(model.getProvider())
                .protocol(ModelProtocol.normalize(model.getProtocol()))
                .baseUrl(endpoint)
                .endpointHost(endpointHost(endpoint))
                .realModelId(model.getModel())
                .apiKeyConfigured(!isBlank(model.getApikey()))
                .capabilities(model.getCapabilities() == null ? List.of() : model.getCapabilities())
                .timeoutSeconds(model.getTimeoutSeconds())
                .contextWindowTokens(model.getContextWindowTokens())
                .inputPricePerMillion(pricing == null ? null : pricing.getInputPerMillion())
                .outputPricePerMillion(pricing == null ? null : pricing.getOutputPerMillion())
                .priceVersion(pricing == null ? null : pricing.getPriceVersion())
                .weight(model.getWeight() == null ? 100 : model.getWeight())
                .priority(model.getPriority() == null ? 100 : model.getPriority())
                .languages(model.getLanguages() == null ? List.of() : model.getLanguages())
                .scenes(model.getScenes() == null ? List.of() : model.getScenes())
                .enabled(model.isEnabled())
                .build();
    }

    private ModelGovernanceSnapshot.ModelGovernanceTier toGovernanceTier(String id,
            ModelTierDefinition tier, Map<String, ModelRuntimeState> stateById) {
        int healthy = 0;
        int degraded = 0;
        int down = 0;
        if (tier != null && tier.getMembers() != null) {
            for (String member : tier.getMembers()) {
                ModelRuntimeState state = stateById.get(member);
                if (state == null || (state.isAvailable() && !"HALF_OPEN".equalsIgnoreCase(state.getPhase()))) {
                    healthy++;
                } else if (state.isAvailable()) {
                    degraded++;
                } else {
                    down++;
                }
            }
        }
        return ModelGovernanceSnapshot.ModelGovernanceTier.builder()
                .id(id)
                .displayName(tier == null ? id : tier.getDisplayName())
                .description(tier == null ? null : tier.getDescription())
                .members(tier == null || tier.getMembers() == null ? List.of() : tier.getMembers())
                .strategy(tier == null ? null : tier.getStrategy())
                .enabled(tier != null && tier.isEnabled())
                .capabilities(tier == null || tier.getCapabilities() == null ? List.of() : tier.getCapabilities())
                .healthyMemberCount(healthy)
                .degradedMemberCount(degraded)
                .downMemberCount(down)
                .build();
    }

    private Map<String, ModelTierDefinition> safeTiers(ModelRoutingProperties config) {
        return config.getTiers() == null ? Map.of() : config.getTiers();
    }

    private List<ModelRoutingProperties.ModelDefinition> safeModels(ModelRoutingProperties config) {
        return config.getModels() == null ? List.of() : config.getModels();
    }

    private List<ModelRuntimeState> safeStates() {
        List<ModelRuntimeState> states = modelStateCenter.listStates();
        return states == null ? List.of() : states;
    }

    private ModelMetricSummary emptyMetrics() {
        return ModelMetricSummary.builder()
                .knownCost(BigDecimal.ZERO)
                .build();
    }

    private double rate(long total, long count) {
        return total == 0 ? 0D : (double) count / (double) total;
    }

    private Double percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return null;
        }
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return (double) sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private boolean isSuccess(ModelCallTrace trace) {
        String status = trace.getStatus();
        return status != null && Set.of("SUCCESS", "SUCCEEDED", "COMPLETED", "OK")
                .contains(status.toUpperCase(Locale.ROOT));
    }

    private boolean isRateLimited(ModelCallTrace trace) {
        String errorCode = trace.getErrorCode();
        String status = trace.getStatus();
        return (errorCode != null && (errorCode.contains("429") || errorCode.toUpperCase(Locale.ROOT).contains("RATE")))
                || (status != null && status.contains("429"));
    }

    private String sanitizeEndpoint(String baseUrl) {
        if (isBlank(baseUrl)) {
            return baseUrl;
        }
        try {
            URI uri = new URI(baseUrl);
            if (uri.getHost() == null) {
                return baseUrl;
            }
            StringBuilder sanitized = new StringBuilder();
            if (uri.getScheme() != null) {
                sanitized.append(uri.getScheme()).append("://");
            }
            sanitized.append(uri.getHost());
            if (uri.getPort() > 0) {
                sanitized.append(':').append(uri.getPort());
            }
            if (uri.getPath() != null) {
                sanitized.append(uri.getPath());
            }
            return sanitized.toString();
        } catch (URISyntaxException exception) {
            return baseUrl;
        }
    }

    private String endpointHost(String endpoint) {
        if (isBlank(endpoint)) {
            return endpoint;
        }
        try {
            URI uri = new URI(endpoint);
            if (uri.getHost() != null) {
                return uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
            }
        } catch (URISyntaxException ignored) {
            // Keep the configured endpoint visible when it is a non-standard provider URL.
        }
        return endpoint;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
