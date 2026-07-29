package com.aseubel.yusi.config.ai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "model.speech.asr")
public class SpeechRecognitionProperties {
    private boolean enabled;
    private String baseUrl;
    private String apiKey;
    private String model = "whisper-1";
    private int timeoutSeconds = 120;
}
