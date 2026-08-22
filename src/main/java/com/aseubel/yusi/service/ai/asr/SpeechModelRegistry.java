package com.aseubel.yusi.service.ai.asr;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.service.ai.asr.adapter.DashScopeStreamingSpeechToTextClient;
import com.aseubel.yusi.service.ai.model.ModelCapability;
import com.aseubel.yusi.service.ai.model.ModelConfigCenter;
import com.aseubel.yusi.service.ai.model.ModelConfigUpdatedEvent;
import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelProtocol;
import com.aseubel.yusi.service.ai.model.ModelRuntimeState;
import com.aseubel.yusi.service.ai.model.ModelSelectionStrategyType;
import com.aseubel.yusi.service.ai.model.ModelStateCenter;
import com.aseubel.yusi.service.ai.model.ModelStrategyRegistry;
import com.aseubel.yusi.service.ai.model.strategy.ModelSelectionStrategy;
import com.aseubel.yusi.service.ai.model.ModelErrorSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves only providers that can keep a live duplex recognition session. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpeechModelRegistry {

    private static final ModelCapability CAPABILITY = ModelCapability.STREAMING_SPEECH_TO_TEXT;

    private final ModelConfigCenter modelConfigCenter;
    private final ModelStrategyRegistry modelStrategyRegistry;
    private final ModelStateCenter modelStateCenter;
    private final Map<String, StreamingSpeechToTextClient> clients = new ConcurrentHashMap<>();
    private final Map<String, ModelRoutingProperties.ModelDefinition> definitions = new ConcurrentHashMap<>();
    private Map<ModelSelectionStrategyType, ModelSelectionStrategy> strategies = Map.of();

    @PostConstruct
    public void init() {
        strategies = modelStrategyRegistry.build();
        reload(modelConfigCenter.getEffectiveConfig());
    }

    @EventListener
    public void handleModelConfigUpdated(ModelConfigUpdatedEvent event) {
        if (event != null && event.getConfig() != null) {
            reload(event.getConfig());
        }
    }

    public synchronized void reload(ModelRoutingProperties config) {
        clients.clear();
        definitions.clear();
        if (config == null || config.getModels() == null) {
            return;
        }
        for (ModelRoutingProperties.ModelDefinition definition : config.getModels()) {
            if (!definition.isEnabled() || !definition.supports(CAPABILITY)
                    || definition.getId() == null || definition.getId().isBlank()) {
                continue;
            }
            StreamingSpeechToTextClient client = createClient(definition);
            if (client != null) {
                clients.put(definition.getId(), client);
                definitions.put(definition.getId(), definition);
            }
        }
    }

    public StreamingSpeechToTextSession startStreaming(StreamingSpeechToTextListener listener) {
        List<SpeechCandidate> candidates = candidates(modelConfigCenter.getEffectiveConfig());
        if (candidates.isEmpty()) {
            throw new IllegalStateException("没有配置可用的流式语音识别模型");
        }

        RuntimeException lastFailure = null;
        for (SpeechCandidate candidate : candidates) {
            if (!modelStateCenter.allowRequest(candidate.instance().getId())) {
                continue;
            }
            long startedAt = System.currentTimeMillis();
            try {
                StreamingSpeechToTextSession session = candidate.client().start(
                        trackingListener(candidate, listener, startedAt));
                log.info("Speech model attempt: operation=speech_stream_start, status=completed, "
                                + "tier={}, strategy={}, modelId={}, modelName={}",
                        candidate.tierId(), candidate.strategy(), candidate.instance().getId(),
                        candidate.instance().getModelName());
                return session;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                recordFailure(candidate, System.currentTimeMillis() - startedAt, exception);
                log.warn("Speech model attempt failed: operation=speech_stream_start, tier={}, strategy={}, "
                                + "modelId={}, exceptionType={}, errorSummary={}",
                        candidate.tierId(), candidate.strategy(), candidate.instance().getId(),
                        com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(exception),
                        ModelErrorSummary.summarize(exception, null));
            }
        }
        throw new IllegalStateException("所有流式语音识别模型均无法启动", lastFailure);
    }

    private StreamingSpeechToTextClient createClient(ModelRoutingProperties.ModelDefinition definition) {
        if (definition.getProvider() == null || definition.getProvider().isBlank()) {
            log.warn("跳过未声明 provider 的流式 ASR 模型: modelId={}", definition.getId());
            return null;
        }
        String provider = definition.getProvider().trim().toLowerCase(Locale.ROOT);
        if ("dashscope".equals(provider)) {
            return new DashScopeStreamingSpeechToTextClient(definition);
        }
        log.warn("跳过不支持流式 ASR 的 provider: modelId={}, provider={}",
                definition.getId(), definition.getProvider());
        return null;
    }

    private List<SpeechCandidate> candidates(ModelRoutingProperties config) {
        if (config == null || config.getTiers() == null) {
            return List.of();
        }
        Map<String, ModelRuntimeState> states = snapshotStates();
        List<SpeechCandidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, ModelTierDefinition> entry : config.getTiers().entrySet()) {
            String tierId = entry.getKey();
            ModelTierDefinition tier = entry.getValue();
            List<String> memberIds = tier == null ? null : tier.getMembers();
            if (tier == null || !tier.isEnabled() || memberIds == null || memberIds.isEmpty()
                    || !supportsStreamingSpeechToText(tier, memberIds)) {
                continue;
            }
            List<ModelInstance> members = memberIds.stream()
                    .map(definitions::get)
                    .filter(definition -> definition != null && clients.containsKey(definition.getId()))
                    .map(this::toInstance)
                    .toList();
            ModelSelectionStrategyType strategyType = tier.getStrategy() == null
                    ? ModelSelectionStrategyType.ROUND_ROBIN : tier.getStrategy();
            ModelSelectionStrategy strategy = strategies.getOrDefault(strategyType,
                    strategies.get(ModelSelectionStrategyType.ROUND_ROBIN));
            if (strategy == null) {
                continue;
            }
            for (ModelInstance instance : strategy.order(tierId, members, states)) {
                if (seen.add(instance.getId())) {
                    result.add(new SpeechCandidate(tierId, strategyType, instance, clients.get(instance.getId())));
                }
            }
            log.debug("Speech tier candidates: tierId={}, strategy={}, members={}",
                    tierId, strategyType, members.stream().map(ModelInstance::getId).toList());
        }
        return List.copyOf(result);
    }

    private boolean supportsStreamingSpeechToText(ModelTierDefinition tier, List<String> memberIds) {
        if (tier.getCapabilities() != null && tier.getCapabilities().contains(CAPABILITY)) {
            return true;
        }
        return memberIds.stream()
                .map(definitions::get)
                .anyMatch(definition -> definition != null && definition.supports(CAPABILITY));
    }

    private Map<String, ModelRuntimeState> snapshotStates() {
        if (definitions.isEmpty()) {
            return Map.of();
        }
        try {
            return modelStateCenter.snapshot(definitions.keySet());
        } catch (RuntimeException exception) {
            log.warn("Speech model state snapshot failed: operation=speech_state_snapshot, exceptionType={}",
                    com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(exception));
            return Map.of();
        }
    }

    private ModelInstance toInstance(ModelRoutingProperties.ModelDefinition definition) {
        Set<ModelCapability> capabilities = definition.getCapabilities() == null
                || definition.getCapabilities().isEmpty()
                ? Set.of(CAPABILITY) : Set.copyOf(definition.getCapabilities());
        return ModelInstance.builder()
                .id(definition.getId())
                .modelName(definition.getModel())
                .provider(definition.getProvider())
                .protocol(ModelProtocol.normalize(definition.getProtocol()))
                .baseUrl(definition.getBaseurl())
                .weight(definition.getWeight() == null ? 100 : definition.getWeight())
                .priority(definition.getPriority() == null ? 100 : definition.getPriority())
                .capabilities(capabilities)
                .scenes(Collections.emptySet())
                .build();
    }

    private StreamingSpeechToTextListener trackingListener(SpeechCandidate candidate,
            StreamingSpeechToTextListener listener, long startedAt) {
        return new StreamingSpeechToTextListener() {
            @Override
            public void onEvent(StreamingTranscriptionEvent event) {
                listener.onEvent(event);
            }

            @Override
            public void onComplete() {
                recordSuccess(candidate, System.currentTimeMillis() - startedAt);
                listener.onComplete();
            }

            @Override
            public void onError(Exception exception) {
                recordFailure(candidate, System.currentTimeMillis() - startedAt, exception);
                listener.onError(exception);
            }
        };
    }

    private void recordSuccess(SpeechCandidate candidate, long latencyMs) {
        try {
            modelStateCenter.recordSuccess(candidate.instance().getId(), candidate.instance().getModelName(), latencyMs);
        } catch (RuntimeException exception) {
            log.warn("Speech model state update failed: operation=speech_state_success, modelId={}, exceptionType={}",
                    candidate.instance().getId(),
                    com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(exception));
        }
    }

    private void recordFailure(SpeechCandidate candidate, long latencyMs, Throwable exception) {
        try {
            modelStateCenter.recordFailure(candidate.instance().getId(), candidate.instance().getModelName(),
                    latencyMs, exception);
        } catch (RuntimeException stateException) {
            log.warn("Speech model state update failed: operation=speech_state_failure, modelId={}, exceptionType={}",
                    candidate.instance().getId(),
                    com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(stateException));
        }
    }

    private record SpeechCandidate(String tierId, ModelSelectionStrategyType strategy,
            ModelInstance instance, StreamingSpeechToTextClient client) {
    }
}
