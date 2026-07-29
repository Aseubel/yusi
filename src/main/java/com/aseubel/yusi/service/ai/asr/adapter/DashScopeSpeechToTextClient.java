package com.aseubel.yusi.service.ai.asr.adapter;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.protocol.ConnectionOptions;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.asr.SpeechToTextClient;
import com.aseubel.yusi.service.ai.asr.TranscriptionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Locale;

@Slf4j
public class DashScopeSpeechToTextClient implements SpeechToTextClient {

    private final ModelRoutingProperties.ModelDefinition definition;
    private final ConnectionOptions connectionOptions;

    public DashScopeSpeechToTextClient(ModelRoutingProperties.ModelDefinition definition) {
        this.definition = definition;
        int timeoutSeconds = Math.max(1, definition.getTimeoutSeconds() == null
                ? 120 : definition.getTimeoutSeconds());
        this.connectionOptions = ConnectionOptions.builder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .writeTimeout(Duration.ofSeconds(timeoutSeconds))
                .readTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Override
    public String modelId() {
        return definition.getId();
    }

    @Override
    public TranscriptionResult transcribe(MultipartFile audio) {
        String format = resolveFormat(audio);
        File tempFile = null;
        try {
            tempFile = Files.createTempFile("yusi-asr-", "." + format).toFile();
            audio.transferTo(tempFile);
            RecognitionParam param = RecognitionParam.builder()
                    .model(definition.getModel())
                    .apiKey(definition.getApikey())
                    .format(format)
                    .sampleRate(16000)
                    .build();
            String text = new Recognition(connectionOptions).call(param, tempFile);
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("DashScope ASR 未返回文本");
            }
            return new TranscriptionResult(modelId(), text.trim());
        } catch (IOException e) {
            throw new IllegalStateException("DashScope ASR 临时文件处理失败", e);
        } catch (RuntimeException e) {
            log.warn("DashScope ASR 调用失败: modelId={}", modelId(), e);
            throw e;
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.debug("DashScope ASR 临时文件清理失败: {}", tempFile);
            }
        }
    }

    private String resolveFormat(MultipartFile audio) {
        String contentType = audio.getContentType();
        if (contentType != null) {
            String normalized = contentType.toLowerCase(Locale.ROOT);
            if (normalized.contains("wav")) {
                return "wav";
            }
            if (normalized.contains("mpeg") || normalized.contains("mp3")) {
                return "mp3";
            }
            if (normalized.contains("pcm")) {
                return "pcm";
            }
            if (normalized.contains("opus") || normalized.contains("ogg")
                    || normalized.contains("webm")) {
                return "opus";
            }
            if (normalized.contains("aac")) {
                return "aac";
            }
            if (normalized.contains("amr")) {
                return "amr";
            }
        }
        throw new IllegalArgumentException("DashScope ASR 暂不支持当前音频格式: " + contentType);
    }
}
