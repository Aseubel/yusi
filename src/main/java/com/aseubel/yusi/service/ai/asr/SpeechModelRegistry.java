package com.aseubel.yusi.service.ai.asr;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.config.ai.properties.ModelTierDefinition;
import com.aseubel.yusi.service.ai.asr.adapter.DashScopeStreamingSpeechToTextClient;
import com.aseubel.yusi.service.ai.model.ModelCapability;
import com.aseubel.yusi.service.ai.model.ModelConfigCenter;
import com.aseubel.yusi.service.ai.model.ModelConfigUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves only providers that can keep a live duplex recognition session. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpeechModelRegistry {

    private static final ModelCapability CAPABILITY = ModelCapability.STREAMING_SPEECH_TO_TEXT;

    private final ModelConfigCenter modelConfigCenter;
    private final Map<String, StreamingSpeechToTextClient> clients = new ConcurrentHashMap<>();
    private final Map<String, ModelRoutingProperties.ModelDefinition> definitions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
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
        List<StreamingSpeechToTextClient> candidates = candidates(modelConfigCenter.getEffectiveConfig());
        if (candidates.isEmpty()) {
            throw new IllegalStateException("没有配置可用的流式语音识别模型");
        }

        RuntimeException lastFailure = null;
        for (StreamingSpeechToTextClient candidate : candidates) {
            try {
                return candidate.start(listener);
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.warn("流式语音识别模型启动失败，尝试下一个模型: modelId={}", candidate.modelId());
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

    private List<StreamingSpeechToTextClient> candidates(ModelRoutingProperties config) {
        if (config == null || config.getTiers() == null) {
            return List.of();
        }
        List<String> memberIds = config.getTiers().entrySet().stream()
                .filter(entry -> {
                    ModelTierDefinition tier = entry.getValue();
                    List<String> members = tier == null ? null : tier.getMembers();
                    return tier != null && tier.isEnabled()
                            && members != null && !members.isEmpty()
                            && supportsStreamingSpeechToText(tier, members);
                })
                .flatMap(entry -> entry.getValue().getMembers().stream())
                .distinct()
                .toList();
        return memberIds.stream()
                .map(clients::get)
                .filter(client -> client != null)
                .sorted(Comparator.comparingInt(client -> {
                    Integer priority = definitions.get(client.modelId()).getPriority();
                    return priority == null ? 100 : priority;
                }))
                .toList();
    }

    private boolean supportsStreamingSpeechToText(ModelTierDefinition tier, List<String> memberIds) {
        if (tier.getCapabilities() != null && tier.getCapabilities().contains(CAPABILITY)) {
            return true;
        }
        return memberIds.stream()
                .map(definitions::get)
                .anyMatch(definition -> definition != null && definition.supports(CAPABILITY));
    }
}
