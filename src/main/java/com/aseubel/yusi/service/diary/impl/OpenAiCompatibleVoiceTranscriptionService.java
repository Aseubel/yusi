package com.aseubel.yusi.service.diary.impl;

import com.aseubel.yusi.config.ai.properties.SpeechRecognitionProperties;
import com.aseubel.yusi.service.diary.VoiceTranscriptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Service
public class OpenAiCompatibleVoiceTranscriptionService implements VoiceTranscriptionService {

    private final SpeechRecognitionProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public OpenAiCompatibleVoiceTranscriptionService(SpeechRecognitionProperties properties,
                                                     ObjectMapper objectMapper,
                                                     RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        int timeoutSeconds = Math.max(1, properties.getTimeoutSeconds());
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(timeoutSeconds))
                .setReadTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Override
    public String transcribe(MultipartFile audio) {
        if (!properties.isEnabled() || properties.getBaseUrl() == null || properties.getApiKey() == null) {
            throw new IllegalStateException("语音识别服务未配置");
        }
        try {
            ByteArrayResource resource = new ByteArrayResource(audio.getBytes()) {
                @Override
                public String getFilename() {
                    return audio.getOriginalFilename() == null ? "voice.webm" : audio.getOriginalFilename();
                }
            };
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);
            body.add("model", properties.getModel());

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(properties.getApiKey());
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getBaseUrl(), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            JsonNode json = objectMapper.readTree(response.getBody());
            String text = json.path("text").asText(null);
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("语音识别未返回文本");
            }
            return text.trim();
        } catch (IOException | RestClientException e) {
            log.error("语音识别失败", e);
            throw new IllegalStateException("语音识别失败", e);
        }
    }
}
