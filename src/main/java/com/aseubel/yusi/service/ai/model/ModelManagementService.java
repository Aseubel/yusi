package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.constant.ModelCallStatus;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.config.ai.properties.RoutePolicyDefinition;
import com.aseubel.yusi.pojo.dto.model.ModelCallTraceItem;
import com.aseubel.yusi.pojo.dto.model.ModelCallTraceQuery;
import com.aseubel.yusi.pojo.dto.model.ModelConfigRestoreRequest;
import com.aseubel.yusi.pojo.dto.model.ModelConfigRestoreResponse;
import com.aseubel.yusi.pojo.dto.model.ModelConfigVersionInfo;
import com.aseubel.yusi.pojo.dto.model.ModelGovernanceSnapshot;
import com.aseubel.yusi.pojo.dto.model.ModelGovernanceUpdateRequest;
import com.aseubel.yusi.pojo.dto.model.ModelMetricSummary;
import com.aseubel.yusi.pojo.dto.model.ModelMetricAggregate;
import com.aseubel.yusi.pojo.dto.model.ModelMetricBucket;
import com.aseubel.yusi.pojo.dto.model.ModelMetricTrendQuery;
import com.aseubel.yusi.pojo.dto.model.ModelMetricTrendResponse;
import com.aseubel.yusi.pojo.dto.model.ModelRouteReason;
import com.aseubel.yusi.pojo.dto.model.ModelRoutePreviewRequest;
import com.aseubel.yusi.pojo.dto.model.ModelRoutePreviewResponse;
import com.aseubel.yusi.pojo.dto.model.ModelRuntimeResetResponse;
import com.aseubel.yusi.pojo.entity.ModelCallTrace;
import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditDetailKeys;
import com.aseubel.yusi.pojo.constant.SecurityAuditOperation;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditReasonCode;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import com.aseubel.yusi.repository.ModelCallTraceRepository;
import com.aseubel.yusi.repository.ModelCallTraceMetricsRepository;
import com.aseubel.yusi.service.security.SecurityAuditService;
import com.aseubel.yusi.service.ai.model.constant.ModelHealthPhase;
import com.aseubel.yusi.service.ai.model.constant.ModelRouteExclusionReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
@Slf4j
public class ModelManagementService {

    private final ModelStateCenter modelStateCenter;
    private final ModelConfigCenter modelConfigCenter;
    private final ModelRoutePlanner modelRoutePlanner;
    private final ModelCallTraceRepository modelCallTraceRepository;
    private final ModelCallTraceMetricsRepository modelCallTraceMetricsRepository;
    private final SecurityAuditService securityAuditService;
    private final ObjectMapper objectMapper;

    public List<ModelRuntimeState> listModelStates() {
        return modelStateCenter.listStates();
    }

    public ModelRuntimeResetResponse resetModelState(String modelId, String operatorId) {
        try {
            requireConfiguredModel(modelId);
            ModelStateCenter.ResetResult result = modelStateCenter.resetWithOutcome(modelId);
            recordResetAudit(operatorId, modelId, "single", result.count(), SecurityAuditOutcome.SUCCESS,
                    SecurityAuditReasonCode.ADMIN_MUTATION);
            return ModelRuntimeResetResponse.builder()
                    .scope("single")
                    .modelId(modelId)
                    .count(result.count())
                    .status(result.convergencePending() ? "RESET_STORED_CONVERGENCE_PENDING" : "RESET")
                    .convergencePending(result.convergencePending())
                    .state(result.state())
                    .build();
        } catch (RuntimeException exception) {
            recordResetAudit(operatorId, modelId, "single", 0, SecurityAuditOutcome.FAILURE,
                    SecurityAuditReasonCode.MODEL_STATE_RESET_FAILED);
            throw exception;
        }
    }

    public ModelRuntimeResetResponse resetAllModelStates(String operatorId) {
        try {
            ModelRoutingProperties config = modelConfigCenter.getEffectiveConfig();
            List<String> configuredIds = safeModels(config).stream()
                    .map(ModelRoutingProperties.ModelDefinition::getId)
                    .filter(Objects::nonNull)
                    .toList();
            ModelStateCenter.ResetResult result = modelStateCenter.resetAllWithOutcome(configuredIds);
            recordResetAudit(operatorId, "all", "all", result.count(), SecurityAuditOutcome.SUCCESS,
                    SecurityAuditReasonCode.ADMIN_MUTATION);
            return ModelRuntimeResetResponse.builder()
                    .scope("all")
                    .modelId("all")
                    .count(result.count())
                    .status(result.convergencePending() ? "RESET_STORED_CONVERGENCE_PENDING" : "RESET")
                    .convergencePending(result.convergencePending())
                    .build();
        } catch (RuntimeException exception) {
            recordResetAudit(operatorId, "all", "all", 0, SecurityAuditOutcome.FAILURE,
                    SecurityAuditReasonCode.MODEL_STATE_RESET_FAILED);
            throw exception;
        }
    }

    public ModelGovernanceSnapshot getGovernanceSnapshot() {
        ModelRoutingProperties config = modelConfigCenter.getEffectiveConfig();
        List<ModelRuntimeState> runtimeStates = safeStates();
        Map<String, ModelRuntimeState> stateById = runtimeStates.stream()
                .filter(Objects::nonNull)
                .filter(state -> state.getInstanceId() != null)
                .collect(Collectors.toMap(ModelRuntimeState::getInstanceId, state -> state,
                        (first, ignored) -> first));

        Map<String, ModelRoutingProperties.ModelDefinition> modelsById = safeModels(config).stream()
                .filter(Objects::nonNull)
                .filter(model -> model.getId() != null)
                .collect(Collectors.toMap(ModelRoutingProperties.ModelDefinition::getId,
                        model -> model, (first, ignored) -> first, LinkedHashMap::new));
        Map<String, List<String>> tierIdsByModel = new HashMap<>();
        safeTiers(config).forEach((tierId, tier) -> {
            if (tier != null && tier.getMembers() != null) {
                tier.getMembers().forEach(modelId -> tierIdsByModel
                        .computeIfAbsent(modelId, ignored -> new ArrayList<>()).add(tierId));
            }
        });
        Map<String, List<String>> routeIdsByModel = new HashMap<>();
        List<RoutePolicyDefinition> routeDefinitions = config.getRoutes() == null
                ? List.of() : config.getRoutes();
        routeDefinitions.forEach(route -> routeModelIds(route, config).forEach(modelId -> routeIdsByModel
                .computeIfAbsent(modelId, ignored -> new ArrayList<>()).add(route.getId())));
        List<ModelGovernanceSnapshot.ModelGovernanceModel> models = safeModels(config).stream()
                .map(model -> toGovernanceModel(model, stateById,
                        tierIdsByModel.getOrDefault(model.getId(), List.of()),
                        routeIdsByModel.getOrDefault(model.getId(), List.of())))
                .toList();
        List<ModelGovernanceSnapshot.ModelGovernanceTier> tiers = safeTiers(config).entrySet().stream()
                .map(entry -> toGovernanceTier(entry.getKey(), entry.getValue(), modelsById, stateById))
                .toList();
        List<ModelGovernanceSnapshot.ModelGovernanceRoute> routeProjections = routeDefinitions.stream()
                .map(route -> toGovernanceRoute(route, config, stateById))
                .toList();
        ModelMetricSummary summary = getMetrics(ModelCallTraceQuery.builder().build());
        ModelGovernanceSnapshot.ModelRuntimeSummary runtimeSummary = runtimeSummary(config, stateById,
                routeProjections);

        return ModelGovernanceSnapshot.builder()
                .version(config.getVersion())
                .schemaVersion(config.getSchemaVersion())
                .defaultScene(config.getDefaultScene())
                .defaultTier(config.getDefaultTier())
                .models(models)
                .tiers(tiers)
                .routes(config.getRoutes() == null ? List.of() : config.getRoutes())
                .defaultRoute(config.getDefaultRoute())
                .runtimeStates(runtimeStates)
                .summary(summary)
                .runtimeSummary(runtimeSummary)
                .lastRefreshedAt(System.currentTimeMillis())
                .routeProjections(routeProjections)
                .build();
    }

    public long updateGovernance(ModelGovernanceUpdateRequest request, String operatorId) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "模型治理配置不能为空");
        }
        ModelRoutingProperties updated = modelConfigCenter.updateCanonical(
                request.toProperties(), request.getExpectedVersion(), operatorId);
        if (securityAuditService != null && operatorId != null && !operatorId.isBlank()) {
            securityAuditService.recordAdmin(SecurityAuditAction.MODEL_GOVERNANCE_UPDATED, operatorId, null,
                    SecurityAuditResourceType.MODEL_GOVERNANCE, "active", SecurityAuditOutcome.SUCCESS,
                    SecurityAuditReasonCode.ADMIN_MUTATION,
                    Map.of(
                            SecurityAuditDetailKeys.OPERATION, SecurityAuditOperation.UPDATE.name(),
                            SecurityAuditDetailKeys.VERSION, String.valueOf(updated.getVersion())));
        }
        return updated.getVersion();
    }

    public List<ModelConfigVersionInfo> listConfigVersions() {
        return modelConfigCenter.listRestoreVersions();
    }

    public ModelGovernanceSnapshot getRestorePreview(String mode, Long version) {
        ModelRoutingProperties target = resolveRestoreTarget(mode, version);
        return toRestorePreviewSnapshot(target);
    }

    public ModelConfigRestoreResponse restoreConfig(ModelConfigRestoreRequest request, String operatorId) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "恢复请求不能为空");
        }
        boolean factory = isFactoryMode(request.getMode());
        if (!factory && request.getVersion() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "历史回滚必须指定 version");
        }
        ModelRoutingProperties target = resolveRestoreTarget(request.getMode(), request.getVersion());
        ModelRoutingProperties restored = modelConfigCenter.restoreCanonical(target,
                request.getExpectedVersion(), operatorId, factory);
        List<String> missingApiKeyModels = safeModels(restored).stream()
                .filter(model -> model.getApikey() == null || model.getApikey().isBlank())
                .map(ModelRoutingProperties.ModelDefinition::getId)
                .toList();
        String action = factory ? "RESTORE_FACTORY" : "ROLLBACK";
        if (securityAuditService != null && operatorId != null && !operatorId.isBlank()) {
            try {
                securityAuditService.recordAdmin(SecurityAuditAction.MODEL_CONFIG_RESTORED, operatorId, null,
                        SecurityAuditResourceType.MODEL_GOVERNANCE, "active", SecurityAuditOutcome.SUCCESS,
                        SecurityAuditReasonCode.ADMIN_MUTATION,
                        Map.of(
                                SecurityAuditDetailKeys.OPERATION, SecurityAuditOperation.UPDATE.name(),
                                SecurityAuditDetailKeys.VERSION, String.valueOf(restored.getVersion()),
                                SecurityAuditDetailKeys.ACTION, action));
            } catch (RuntimeException auditException) {
                log.warn("Model config restore audit failed: exceptionType={}",
                        com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(auditException));
            }
        }
        return ModelConfigRestoreResponse.builder()
                .version(restored.getVersion())
                .action(action)
                .missingApiKeyModels(missingApiKeyModels)
                .build();
    }

    private boolean isFactoryMode(String mode) {
        if ("FACTORY".equalsIgnoreCase(mode == null ? "" : mode.trim())) {
            return true;
        }
        if ("VERSION".equalsIgnoreCase(mode == null ? "" : mode.trim())) {
            return false;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "mode 只能是 FACTORY 或 VERSION");
    }

    private ModelRoutingProperties resolveRestoreTarget(String mode, Long version) {
        if (isFactoryMode(mode)) {
            return modelConfigCenter.getFactoryDefaultConfig();
        }
        if (version == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "历史回滚必须指定 version");
        }
        return modelConfigCenter.getRestoreSnapshot(version);
    }

    /** 仅装配配置字段（models/tiers/routes/defaultRoute）的快照，供前端恢复预览复用 createGovernanceDraft。 */
    private ModelGovernanceSnapshot toRestorePreviewSnapshot(ModelRoutingProperties config) {
        Map<String, ModelRoutingProperties.ModelDefinition> modelsById = safeModels(config).stream()
                .filter(model -> model != null && model.getId() != null)
                .collect(Collectors.toMap(ModelRoutingProperties.ModelDefinition::getId,
                        model -> model, (first, ignored) -> first, LinkedHashMap::new));
        Map<String, List<String>> tierIdsByModel = new HashMap<>();
        safeTiers(config).forEach((tierId, tier) -> {
            if (tier != null && tier.getMembers() != null) {
                tier.getMembers().forEach(modelId -> tierIdsByModel
                        .computeIfAbsent(modelId, ignored -> new ArrayList<>()).add(tierId));
            }
        });
        List<RoutePolicyDefinition> routeDefinitions = config.getRoutes() == null
                ? List.of() : config.getRoutes();
        Map<String, List<String>> routeIdsByModel = new HashMap<>();
        routeDefinitions.forEach(route -> routeModelIds(route, config).forEach(modelId -> routeIdsByModel
                .computeIfAbsent(modelId, ignored -> new ArrayList<>()).add(route.getId())));

        return ModelGovernanceSnapshot.builder()
                .version(config.getVersion())
                .schemaVersion(config.getSchemaVersion())
                .defaultScene(config.getDefaultScene())
                .defaultTier(config.getDefaultTier())
                .models(safeModels(config).stream()
                        .map(model -> toGovernanceModel(model, Map.of(),
                                tierIdsByModel.getOrDefault(model.getId(), List.of()),
                                routeIdsByModel.getOrDefault(model.getId(), List.of())))
                        .toList())
                .tiers(safeTiers(config).entrySet().stream()
                        .map(entry -> toGovernanceTier(entry.getKey(), entry.getValue(), modelsById, Map.of()))
                        .toList())
                .routes(routeDefinitions)
                .defaultRoute(config.getDefaultRoute())
                .runtimeStates(List.of())
                .summary(emptyMetrics())
                .lastRefreshedAt(System.currentTimeMillis())
                .routeProjections(List.of())
                .build();
    }

    public ModelRoutePreviewResponse previewRoute(ModelRoutePreviewRequest request) {
        if (request == null || isBlank(request.getScene())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "scene 不能为空");
        }
        if (request.getEstimatedInputTokens() != null && request.getEstimatedInputTokens() < 0
                || request.getReservedOutputTokens() != null && request.getReservedOutputTokens() < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "token 估算值不能为负数");
        }
        if (!isBlank(request.getRiskLevel())
                && !Set.of("LOW", "MEDIUM", "HIGH", "*")
                        .contains(request.getRiskLevel().trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "riskLevel 只能是 LOW、MEDIUM、HIGH 或 *");
        }
        ModelRoutingProperties properties = previewProperties(request.getDraft());
        ModelRouteContext context = ModelRouteContext.builder()
                .scene(request.getScene())
                .riskLevel(request.getRiskLevel())
                .estimatedInputTokens(request.getEstimatedInputTokens())
                .reservedOutputTokens(request.getReservedOutputTokens())
                .build();
        final ModelRouteDecision decision;
        try {
            Map<String, List<ModelInstance>> tierMembers = metadataTierMembers(properties);
            Set<String> modelIds = tierMembers.values().stream().flatMap(List::stream)
                    .map(ModelInstance::getId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<String, ModelRuntimeState> states = snapshotStates(modelIds);
            decision = modelRoutePlanner.plan(properties, context, tierMembers, states);
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
                        .exclusionExplanation(candidate.exclusionExplanation())
                        .rank(candidate.rank())
                        .fallback(candidate.fallback())
                        .strategy(candidate.strategy() == null ? null : candidate.strategy().name())
                        .priority(candidate.priority())
                        .weight(candidate.weight())
                        .avgLatencyMs(candidate.avgLatencyMs())
                        .phase(candidate.phase())
                        .attemptable(candidate.attemptable())
                        .build())
                .toList();
        Set<String> warnings = new LinkedHashSet<>();
        decision.candidates().stream()
                .filter(candidate -> candidate.excludedReason() != null
                        && !ModelRouteExclusionReason.FALLBACK_TIER.code().equals(candidate.excludedReason()))
                .forEach(candidate -> warnings.add("excluded:" + candidate.modelId()
                        + ":" + candidate.excludedReason()));
        decision.candidates().stream()
                .filter(candidate -> candidate.available())
                .map(ModelRouteCandidate::modelId)
                .map(id -> safeModels(properties).stream()
                        .filter(model -> Objects.equals(model.getId(), id)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .filter(model -> model.getPricing() == null
                        || model.getPricing().getInputPerMillion() == null
                        || model.getPricing().getOutputPerMillion() == null)
                .forEach(model -> warnings.add("unknown-cost:" + model.getId()));
        decision.candidates().stream()
                .filter(candidate -> "UNKNOWN".equals(candidate.phase()))
                .map(ModelRouteCandidate::modelId)
                .filter(Objects::nonNull)
                .forEach(modelId -> warnings.add("cold-start:" + modelId));
        if (decision.attemptCandidates().isEmpty()) {
            warnings.add("no-available-candidate");
        }
        return ModelRoutePreviewResponse.builder()
                .policyId(decision.policyId())
                .primaryTier(decision.primaryTier())
                .candidates(candidates)
                .routeReason(decision.routeReason())
                .routeReasonDetails(ModelRouteReason.builder()
                        .routeId(decision.routeReasonDetails().routeId())
                        .sceneMatchLevel(decision.routeReasonDetails().sceneMatchLevel())
                        .riskMatchLevel(decision.routeReasonDetails().riskMatchLevel())
                        .routePriority(decision.routeReasonDetails().routePriority())
                        .primaryTier(decision.routeReasonDetails().primaryTier())
                        .fallbackTierOrder(decision.routeReasonDetails().fallbackTierOrder())
                        .strategyOrder(decision.routeReasonDetails().strategyOrder())
                        .build())
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
        if (modelCallTraceMetricsRepository == null) {
            return emptyMetrics();
        }
        ModelMetricAggregate aggregate = modelCallTraceMetricsRepository.aggregate(buildSpecification(safeQuery));
        long totalTokens = ModelTokenBudget.saturatingAdd(aggregate.inputTokens(), aggregate.outputTokens());
        return ModelMetricSummary.builder()
                .callCount(aggregate.callCount())
                .totalTokens(totalTokens)
                .routeCount(aggregate.callCount())
                .fallbackCount(aggregate.fallbackCount())
                .fallbackRate(aggregate.fallbackRate())
                .successRate(aggregate.successRate())
                .averageLatencyMs(aggregate.averageLatencyMs())
                .p95LatencyMs(aggregate.p95LatencyMs())
                .rateLimitedCount(aggregate.rateLimitedCount())
                .errorCount(aggregate.errorCount())
                .inputTokens(aggregate.inputTokens())
                .outputTokens(aggregate.outputTokens())
                .knownCost(aggregate.knownCost())
                .unknownCostCount(aggregate.unknownCostCount())
                .build();
    }

    public ModelMetricTrendResponse getMetricTrend(ModelMetricTrendQuery query) {
        ModelMetricTrendQuery safeQuery = query == null ? new ModelMetricTrendQuery() : query;
        validateMetricTrendQuery(safeQuery);
        ModelMetricTrendQuery.Bucket bucket = safeQuery.getBucket() == null
                ? ModelMetricTrendQuery.Bucket.HOUR : safeQuery.getBucket();
        List<ModelMetricBucket> items = modelCallTraceMetricsRepository == null
                ? List.of()
                : modelCallTraceMetricsRepository.aggregateTrend(
                        buildSpecification(safeQuery.toTraceQuery()), bucket);
        return ModelMetricTrendResponse.builder()
                .bucket(bucket)
                .from(safeQuery.getFrom())
                .to(safeQuery.getTo())
                .items(items)
                .build();
    }

    private void validateMetricTrendQuery(ModelMetricTrendQuery query) {
        if (query.getBucket() == null) {
            query.setBucket(ModelMetricTrendQuery.Bucket.HOUR);
        }
        if (query.getFrom() != null && query.getTo() != null) {
            if (!query.getFrom().isBefore(query.getTo())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "指标时间范围必须满足 from < to");
            }
            if (query.getFrom().plusDays(366).isBefore(query.getTo())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "指标时间范围不能超过 366 天");
            }
        }
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
            addLike(predicates, criteriaBuilder, root, "userId", query.getUserId());
            addLike(predicates, criteriaBuilder, root, "runId", query.getRunId());
            addLike(predicates, criteriaBuilder, root, "promptKey", query.getPromptKey());
            addLike(predicates, criteriaBuilder, root, "promptVersion", query.getPromptVersion());
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
            ModelRoutingProperties.ModelDefinition model, Map<String, ModelRuntimeState> stateById,
            List<String> tierIds, List<String> routeIds) {
        ModelRoutingProperties.PricingDefinition pricing = model.getPricing();
        String endpoint = sanitizeEndpoint(model.getBaseurl());
        ModelRuntimeState state = stateById.get(model.getId());
        String status = runtimeStatus(model, state);
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
                .scenes(model.getScenes() == null ? List.of() : model.getScenes())
                .enabled(model.isEnabled())
                .runtimeStatus(status)
                .phase(state == null ? "UNKNOWN" : state.getPhase())
                .available(state == null || state.isAvailable())
                .consecutiveFailures(state == null ? 0 : state.getConsecutiveFailures())
                .avgLatencyMs(state == null ? 0D : state.getAvgLatencyMs())
                .errorRate(state == null ? 0D : state.getErrorRate())
                .lastError(state == null ? null : state.getLastError())
                .lastUpdatedAt(state == null ? 0L : state.getLastUpdatedAt())
                .tierIds(tierIds == null ? List.of() : List.copyOf(tierIds))
                .routeIds(routeIds == null ? List.of() : List.copyOf(routeIds))
                .build();
    }

    private ModelGovernanceSnapshot.ModelGovernanceTier toGovernanceTier(String id,
            ModelTierDefinition tier, Map<String, ModelRoutingProperties.ModelDefinition> modelsById,
            Map<String, ModelRuntimeState> stateById) {
        int healthy = 0;
        int degraded = 0;
        int down = 0;
        int unknown = 0;
        List<ModelGovernanceSnapshot.ModelGovernanceTierMember> memberDetails = new ArrayList<>();
        if (tier != null && tier.getMembers() != null) {
            for (String member : tier.getMembers()) {
                ModelRoutingProperties.ModelDefinition model = modelsById.get(member);
                ModelRuntimeState state = stateById.get(member);
                String status = runtimeStatus(model, state);
                if ("UNKNOWN".equals(status)) {
                    unknown++;
                } else if ("HALF_OPEN".equals(status)) {
                    degraded++;
                } else if ("DOWN".equals(status)) {
                    down++;
                } else if ("UP".equals(status)) {
                    healthy++;
                }
                memberDetails.add(ModelGovernanceSnapshot.ModelGovernanceTierMember.builder()
                        .modelId(member)
                        .priority(model == null || model.getPriority() == null ? 100 : model.getPriority())
                        .weight(model == null || model.getWeight() == null ? 100 : model.getWeight())
                        .runtimeStatus(status)
                        .phase(state == null ? "UNKNOWN" : state.getPhase())
                        .available(state == null || state.isAvailable())
                        .avgLatencyMs(state == null ? 0D : state.getAvgLatencyMs())
                        .build());
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
                .unknownMemberCount(unknown)
                .memberCount(tier == null || tier.getMembers() == null ? 0 : tier.getMembers().size())
                .memberDetails(memberDetails)
                .build();
    }

    private ModelGovernanceSnapshot.ModelGovernanceRoute toGovernanceRoute(RoutePolicyDefinition route,
            ModelRoutingProperties config, Map<String, ModelRuntimeState> stateById) {
        List<ModelGovernanceSnapshot.ModelGovernanceTierReference> fallback = route.getFallbackTiers() == null
                ? List.of() : route.getFallbackTiers().stream()
                        .map(tierId -> tierReference(tierId, route.getScene(), config, stateById))
                        .toList();
        ModelGovernanceSnapshot.ModelGovernanceTierReference primary = tierReference(
                route.getPrimaryTier(), route.getScene(), config, stateById);
        boolean available = primary.isAvailable() || fallback.stream()
                .anyMatch(ModelGovernanceSnapshot.ModelGovernanceTierReference::isAvailable);
        return ModelGovernanceSnapshot.ModelGovernanceRoute.builder()
                .id(route.getId())
                .scene(route.getScene())
                .riskLevel(route.getRiskLevel())
                .priority(route.getPriority())
                .enabled(route.isEnabled())
                .primaryTier(route.getPrimaryTier())
                .primaryStrategy(primary.getStrategy())
                .fallbackTiers(fallback)
                .available(available)
                .runtimeStatus(available ? (primary.isAvailable() ? primary.getRuntimeStatus() : "FALLBACK_ONLY") : "DOWN")
                .build();
    }

    private ModelGovernanceSnapshot.ModelGovernanceTierReference tierReference(String tierId, String scene,
            ModelRoutingProperties config, Map<String, ModelRuntimeState> stateById) {
        ModelTierDefinition tier = safeTiers(config).get(tierId);
        if (tier == null || !tier.isEnabled() || tier.getMembers() == null) {
            return ModelGovernanceSnapshot.ModelGovernanceTierReference.builder()
                    .id(tierId).strategy(tier == null ? null : tier.getStrategy())
                    .available(false).runtimeStatus("DOWN").build();
        }
        boolean any = false;
        boolean unknown = false;
        boolean halfOpen = false;
        for (String memberId : tier.getMembers()) {
            ModelRoutingProperties.ModelDefinition model = safeModels(config).stream()
                    .filter(candidate -> Objects.equals(candidate.getId(), memberId)).findFirst().orElse(null);
            if (model == null || !model.isEnabled() || !supportsScene(model, scene)
                    || !supportsTierCapabilities(tier, model)) {
                continue;
            }
            ModelRuntimeState state = stateById.get(memberId);
            if (state == null) {
                unknown = true;
                any = true;
            } else if (state.isAvailable()) {
                any = true;
                halfOpen |= ModelHealthPhase.HALF_OPEN.code().equalsIgnoreCase(state.getPhase());
            }
        }
        return ModelGovernanceSnapshot.ModelGovernanceTierReference.builder()
                .id(tierId)
                .strategy(tier.getStrategy())
                .available(any)
                .runtimeStatus(any ? (halfOpen ? "HALF_OPEN" : unknown ? "UNKNOWN" : "UP") : "DOWN")
                .build();
    }

    private ModelGovernanceSnapshot.ModelRuntimeSummary runtimeSummary(ModelRoutingProperties config,
            Map<String, ModelRuntimeState> stateById,
            List<ModelGovernanceSnapshot.ModelGovernanceRoute> routes) {
        int up = 0;
        int unknown = 0;
        int halfOpen = 0;
        int down = 0;
        for (ModelRoutingProperties.ModelDefinition model : safeModels(config)) {
            if (!model.isEnabled()) {
                continue;
            }
            String status = runtimeStatus(model, stateById.get(model.getId()));
            switch (status) {
                case "UP" -> up++;
                case "HALF_OPEN" -> halfOpen++;
                case "DOWN" -> down++;
                default -> unknown++;
            }
        }
        int noAvailableRoutes = (int) routes.stream().filter(route -> !route.isAvailable()).count();
        return ModelGovernanceSnapshot.ModelRuntimeSummary.builder()
                .upCount(up).unknownCount(unknown).halfOpenCount(halfOpen).downCount(down)
                .noAvailableRouteCount(noAvailableRoutes).build();
    }

    private ModelRoutingProperties previewProperties(JsonNode draftNode) {
        if (draftNode == null || draftNode.isNull()) {
            return modelConfigCenter.getEffectiveConfig();
        }
        if (containsSecretField(draftNode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "路由预览草稿不能包含 api key 或其他 secret 字段");
        }
        try {
            ObjectMapper mapper = objectMapper == null ? new ObjectMapper() : objectMapper;
            ModelGovernanceUpdateRequest draft = mapper.treeToValue(draftNode,
                    ModelGovernanceUpdateRequest.class);
            ModelRoutingProperties properties = draft.toProperties();
            properties.setVersion(draft.getExpectedVersion());
            modelConfigCenter.validateForAdmin(properties);
            return properties;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "路由预览草稿格式无效");
        }
    }

    private boolean containsSecretField(JsonNode node) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String name = field.getKey().replace("_", "").replace("-", "")
                        .toLowerCase(Locale.ROOT);
                if (name.equals("apikey") || name.equals("secret") || name.equals("authorization")
                        || name.equals("accesstoken")) {
                    return true;
                }
                if (containsSecretField(field.getValue())) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsSecretField(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<String, List<ModelInstance>> metadataTierMembers(ModelRoutingProperties properties) {
        Map<String, List<ModelInstance>> result = new LinkedHashMap<>();
        Map<String, ModelRoutingProperties.ModelDefinition> modelsById = safeModels(properties).stream()
                .filter(model -> model != null && model.getId() != null)
                .collect(Collectors.toMap(ModelRoutingProperties.ModelDefinition::getId,
                        model -> model, (first, ignored) -> first));
        safeTiers(properties).forEach((tierId, tier) -> {
            List<ModelInstance> members = new ArrayList<>();
            if (tier != null && tier.getMembers() != null) {
                tier.getMembers().forEach(memberId -> {
                    ModelRoutingProperties.ModelDefinition model = modelsById.get(memberId);
                    members.add(metadataInstance(model));
                });
            }
            result.put(tierId, members);
        });
        return result;
    }

    private ModelInstance metadataInstance(ModelRoutingProperties.ModelDefinition definition) {
        if (definition == null) {
            return ModelInstance.builder().id("unknown").enabled(false).registered(false).build();
        }
        ModelRoutingProperties.PricingDefinition pricing = definition.getPricing();
        Set<String> scenes = definition.getScenes() == null ? Set.of() : definition.getScenes().stream()
                .filter(Objects::nonNull)
                .map(scene -> scene.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<ModelCapability> capabilities = definition.getCapabilities() == null
                || definition.getCapabilities().isEmpty()
                ? Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT)
                : Set.copyOf(definition.getCapabilities());
        return ModelInstance.builder()
                .id(definition.getId())
                .modelName(definition.getModel())
                .provider(definition.getProvider())
                .protocol(ModelProtocol.normalize(definition.getProtocol()))
                .baseUrl(definition.getBaseurl())
                .enabled(definition.isEnabled())
                .registered(true)
                .weight(definition.getWeight() == null ? 100 : definition.getWeight())
                .priority(definition.getPriority() == null ? 100 : definition.getPriority())
                .scenes(scenes)
                .capabilities(capabilities)
                .contextWindowTokens(definition.getContextWindowTokens())
                .inputPricePerMillion(pricing == null ? null : pricing.getInputPerMillion())
                .outputPricePerMillion(pricing == null ? null : pricing.getOutputPerMillion())
                .priceVersion(pricing == null ? null : pricing.getPriceVersion())
                .build();
    }

    private Map<String, ModelRuntimeState> snapshotStates(Set<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return Map.of();
        }
        try {
            return modelStateCenter.snapshot(modelIds);
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    private void requireConfiguredModel(String modelId) {
        if (isBlank(modelId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "modelId 不能为空");
        }
        boolean exists = safeModels(modelConfigCenter.getEffectiveConfig()).stream()
                .anyMatch(model -> model != null && modelId.trim().equals(model.getId()));
        if (!exists) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "模型不存在: " + modelId.trim());
        }
    }

    private void recordResetAudit(String operatorId, String resourceId, String scope, int count,
            SecurityAuditOutcome outcome, String reasonCode) {
        if (securityAuditService == null || isBlank(operatorId)) {
            return;
        }
        try {
            securityAuditService.recordAdmin(SecurityAuditAction.MODEL_RUNTIME_STATE_RESET, operatorId, null,
                    SecurityAuditResourceType.MODEL_GOVERNANCE, resourceId,
                    outcome, reasonCode,
                    Map.of(
                            SecurityAuditDetailKeys.OPERATION, SecurityAuditOperation.RESET.name(),
                            SecurityAuditDetailKeys.SCOPE, scope,
                            SecurityAuditDetailKeys.COUNT, String.valueOf(count)));
        } catch (RuntimeException exception) {
            log.warn("Model state reset audit failed: operation=model_state_reset_audit, scope={}, "
                            + "exceptionType={}", scope,
                    com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(exception));
        }
    }

    private boolean supportsScene(ModelRoutingProperties.ModelDefinition model, String scene) {
        return ModelCapabilityPolicy.supportsScene(model, scene) && (model.getScenes() == null
                || model.getScenes().isEmpty() || model.getScenes().stream().filter(Objects::nonNull)
                        .anyMatch(value -> value.trim().equalsIgnoreCase(scene)));
    }

    private boolean supportsTierCapabilities(ModelTierDefinition tier,
            ModelRoutingProperties.ModelDefinition model) {
        return tier == null || tier.getCapabilities() == null || tier.getCapabilities().isEmpty()
                || tier.getCapabilities().stream().allMatch(model::supports);
    }

    private String runtimeStatus(ModelRoutingProperties.ModelDefinition model, ModelRuntimeState state) {
        if (model == null || !model.isEnabled()) {
            return "DISABLED";
        }
        if (state == null) {
            return "UNKNOWN";
        }
        if (ModelHealthPhase.HALF_OPEN.code().equalsIgnoreCase(state.getPhase())) {
            return "HALF_OPEN";
        }
        return state.isAvailable() ? "UP" : "DOWN";
    }

    private List<String> routeModelIds(RoutePolicyDefinition route, ModelRoutingProperties config) {
        Set<String> tierIds = new LinkedHashSet<>();
        if (route != null) {
            if (route.getPrimaryTier() != null) tierIds.add(route.getPrimaryTier());
            if (route.getFallbackTiers() != null) tierIds.addAll(route.getFallbackTiers());
        }
        return tierIds.stream().flatMap(tierId -> {
            ModelTierDefinition tier = safeTiers(config).get(tierId);
            return tier == null || tier.getMembers() == null ? java.util.stream.Stream.<String>empty()
                    : tier.getMembers().stream();
        }).distinct().toList();
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
                .callCount(0L)
                .totalTokens(0L)
                .routeCount(0L)
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
        return ModelCallStatus.isSuccess(status);
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
