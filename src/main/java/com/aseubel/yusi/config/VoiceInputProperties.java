package com.aseubel.yusi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yusi.diary.voice")
public class VoiceInputProperties {

    private int maxDurationSeconds = 120;
    private int handshakeTimeoutSeconds = 10;
    private int idleTimeoutSeconds = 20;
    private int maxFrameBytes = 64 * 1024;
    private long maxAudioBytes = 3_840_000L;
    private int sendTimeLimitMillis = 10_000;
    private int sendBufferSizeBytes = 512 * 1024;
}
