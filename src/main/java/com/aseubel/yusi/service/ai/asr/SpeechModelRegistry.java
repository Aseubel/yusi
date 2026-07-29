package com.aseubel.yusi.service.ai.asr;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.model.ModelCapability;
import com.aseubel.yusi.service.ai.model.ModelConfigCenter;
import com.aseubel.yusi.service.ai.model.ModelConfigUpdatedEvent;
import com.aseubel.yusi.service.ai.asr.adapter.DashScopeSpeechToTextClient;
import com.aseubel.yusi.service.ai.asr.adapter.OpenAiSpeechToTextClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpeechModelRegistry {

    private static final ModelCapability CAPABILITY = ModelCapability.SPEECH_TO_TEXT;
    private static final String DEFAULT_GROUP = "asr-default";

    private final ModelConfigCenter modelConfigCenter;
    private final ObjectMapper objectMapper;
    private final RestTemplateBuilder restTemplateBuilder;
    private final Map<String, SpeechToTextClient> clients = new ConcurrentHashMap<>();
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
            SpeechToTextClient client = createClient(definition);
            if (client != null) {
                clients.put(definition.getId(), client);
                definitions.put(definition.getId(), definition);
            }
        }
    }

    public TranscriptionResult transcribe(MultipartFile audio) {
        List<SpeechToTextClient> candidates = candidates(modelConfigCenter.getEffectiveConfig());
        if (candidates.isEmpty()) {
            throw new IllegalStateException("没有配置可用的语音识别模型");
        }

        RuntimeException lastFailure = null;
        for (SpeechToTextClient candidate : candidates) {
            try {
                return candidate.transcribe(audio);
            } catch (RuntimeException e) {
                lastFailure = e;
                log.warn("语音识别模型失败，尝试下一个模型: modelId={}", candidate.modelId());
            }
        }
        throw new IllegalStateException("所有语音识别模型均调用失败", lastFailure);
    }

    private SpeechToTextClient createClient(ModelRoutingProperties.ModelDefinition definition) {
        String provider = definition.getProvider() == null
                ? "openai" : definition.getProvider().trim().toLowerCase();
        return switch (provider) {
            case "openai", "openai-compatible" ->
                    new OpenAiSpeechToTextClient(definition, objectMapper, restTemplateBuilder);
            case "dashscope" ->
                    new DashScopeSpeechToTextClient(definition);
            default -> {
                log.warn("跳过不支持的 ASR provider: modelId={}, provider={}",
                        definition.getId(), definition.getProvider());
                yield null;
            }
        };
    }

    private List<SpeechToTextClient> candidates(ModelRoutingProperties config) {
        Map<String, String> capabilityGroups = config.getCapabilityGroups();
        String groupId = capabilityGroups == null
                ? DEFAULT_GROUP : capabilityGroups.getOrDefault(CAPABILITY.name(),
                capabilityGroups.getOrDefault("speech-to-text", DEFAULT_GROUP));
        ModelRoutingProperties.GroupDefinition group = config.getGroups().get(groupId);
        List<String> memberIds = group == null || group.getMembers() == null
                ? new ArrayList<>(clients.keySet()) : group.getMembers();
        return memberIds.stream()
                .map(clients::get)
                .filter(client -> client != null)
                .sorted(Comparator.comparingInt(client ->
                        definitions.get(client.modelId()).getPriority() == null
                                ? 100 : definitions.get(client.modelId()).getPriority()))
                .toList();
    }
}
