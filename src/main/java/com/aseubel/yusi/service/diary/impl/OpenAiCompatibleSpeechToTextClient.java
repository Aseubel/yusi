package com.aseubel.yusi.service.diary.impl;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.diary.SpeechToTextClient;
import com.aseubel.yusi.service.diary.TranscriptionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

@Slf4j
public class OpenAiCompatibleSpeechToTextClient implements SpeechToTextClient {

    private final ModelRoutingProperties.ModelDefinition definition;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public OpenAiCompatibleSpeechToTextClient(ModelRoutingProperties.ModelDefinition definition,
                                              ObjectMapper objectMapper,
                                              RestTemplateBuilder restTemplateBuilder) {
        this.definition = definition;
        this.objectMapper = objectMapper;
        int timeoutSeconds = Math.max(1, definition.getTimeoutSeconds() == null
                ? 120 : definition.getTimeoutSeconds());
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(timeoutSeconds))
                .setReadTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Override
    public String modelId() {
        return definition.getId();
    }

    @Override
    public TranscriptionResult transcribe(MultipartFile audio) {
        try {
            ByteArrayResource resource = new ByteArrayResource(audio.getBytes()) {
                @Override
                public String getFilename() {
                    return audio.getOriginalFilename() == null ? "voice.webm" : audio.getOriginalFilename();
                }
            };
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);
            body.add("model", definition.getModel());

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(definition.getApikey());
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<String> response = restTemplate.exchange(
                    definition.getBaseurl(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);
            JsonNode json = objectMapper.readTree(response.getBody());
            String text = json.path("text").asText(null);
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("语音识别未返回文本");
            }
            return new TranscriptionResult(modelId(), text.trim());
        } catch (IOException | RestClientException e) {
            log.warn("语音识别调用失败: modelId={}", modelId(), e);
            throw new IllegalStateException("语音识别调用失败", e);
        }
    }
}
